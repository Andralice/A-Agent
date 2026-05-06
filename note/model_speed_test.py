#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenAI 兼容 Chat Completions 接口 —— 模型响应速度测试（非流式）。

润色模拟说明（与 Java PolishingAgent 对齐）：
  - 使用 note 里「polish-debug」导出的 txt 时，**默认会去掉文件头**（# 润色请求快照 … ---），
    **仅发送 --- 之后的内容**。这一段就是后端发给模型的完整「用户消息」：
    含管线说明、大纲/设定背景、待润色正文等，已与线上润色请求一致。
  - 若你希望 **连导出文件的头部说明行也一并作为正文发出去**（一般不模拟线上），加 --keep-polish-header。
  - 若需要 **额外 system 角色**（线上默认无单独 system，全靠 user 一条），可用 --system / --system-file。
  - 完全自定义多轮/多段：--messages-json messages.json

用法示例：
  set OPENAI_API_KEY=sk-xxx
  set OPENAI_BASE_URL=https://api.meai.cloud
  python model_speed_test.py --model glm-5.1
  python model_speed_test.py --model qwen-turbo --max-tokens 4096 \\
      --prompt-file ../logs/polish-debug/polish-LIGHT_NOVEL-ch2-xxx.txt
  python model_speed_test.py --model glm-5.1 --prompt-file polish.txt --keep-polish-header
  python model_speed_test.py --model x --system-file role.txt --prompt-file body.txt

依赖：pip install requests
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from typing import Any

try:
    import requests
except ImportError:
    print("请先安装: pip install requests", file=sys.stderr)
    sys.exit(1)


def chat_completions_url(base_url: str) -> str:
    base = base_url.rstrip("/")
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def is_polish_debug_dump(raw: str) -> bool:
    head = raw[:1200] if raw else ""
    return "# 润色请求快照" in head or "promptChars=" in head


def load_text_file(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def extract_polish_user_message(raw: str, *, strip_header: bool) -> str:
    """strip_header=True：与线上一致，只取 --- 之后；False：整文件。"""
    if not strip_header and raw:
        return raw.strip()
    if strip_header and is_polish_debug_dump(raw):
        sep = "\n---\n"
        i = raw.find(sep)
        if i != -1:
            return raw[i + len(sep) :].strip()
    return raw.strip()


def load_user_payload(
    prompt: str | None,
    prompt_file: str | None,
    default_prompt: str,
    *,
    keep_polish_header: bool,
) -> str:
    if prompt:
        return prompt.strip()
    if prompt_file:
        raw = load_text_file(prompt_file)
        strip = not keep_polish_header
        return extract_polish_user_message(raw, strip_header=strip)
    return default_prompt


def load_messages_json(path: str) -> list[dict[str, Any]]:
    raw = load_text_file(path)
    data = json.loads(raw)
    if not isinstance(data, list):
        raise ValueError("messages-json 必须是 JSON 数组")
    out: list[dict[str, Any]] = []
    for i, item in enumerate(data):
        if not isinstance(item, dict):
            raise ValueError(f"messages[{i}] 必须是对象")
        role = item.get("role")
        content = item.get("content")
        if role not in ("system", "user", "assistant"):
            raise ValueError(f"messages[{i}].role 非法: {role}")
        if not isinstance(content, str):
            raise ValueError(f"messages[{i}].content 必须是字符串")
        out.append({"role": role, "content": content})
    return out


def build_messages(
    user_content: str,
    system_text: str | None,
    system_file: str | None,
) -> list[dict[str, str]]:
    msgs: list[dict[str, str]] = []
    st = system_text.strip() if system_text else ""
    if system_file:
        st = st + ("\n" if st else "") + load_text_file(system_file).strip()
    if st:
        msgs.append({"role": "system", "content": st.strip()})
    msgs.append({"role": "user", "content": user_content})
    return msgs


def run_once(
    url: str,
    api_key: str,
    model: str,
    messages: list[dict[str, str]],
    max_tokens: int,
    temperature: float,
    timeout_sec: float,
) -> tuple[float, int, int, str | None]:
    """返回 (elapsed_seconds, http_status, completion_chars, error_message)"""
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    body: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
    }
    err: str | None = None
    t0 = time.perf_counter()
    try:
        r = requests.post(url, headers=headers, json=body, timeout=timeout_sec)
        elapsed = time.perf_counter() - t0
        text_len = 0
        if r.ok:
            try:
                data = r.json()
                choices = data.get("choices") or []
                if choices:
                    msg = choices[0].get("message") or {}
                    content = msg.get("content") or ""
                    text_len = len(content)
            except json.JSONDecodeError:
                err = "响应非 JSON"
        else:
            err = r.text[:500] if r.text else f"HTTP {r.status_code}"
        return elapsed, r.status_code, text_len, err
    except requests.Timeout:
        elapsed = time.perf_counter() - t0
        return elapsed, 0, 0, f"超时 (> {timeout_sec}s)"
    except requests.RequestException as e:
        elapsed = time.perf_counter() - t0
        return elapsed, 0, 0, str(e)


def summarize_messages(messages: list[dict[str, str]]) -> str:
    parts = []
    total = 0
    for m in messages:
        c = m.get("content") or ""
        total += len(c)
        parts.append(f"{m.get('role')}:{len(c)}chars")
    return f"messages=[{', '.join(parts)}] total_chars≈{total}"


def main() -> None:
    default_prompt = (
        "用两三句话说明「网络小说润色」主要改什么，不要列提纲。"
    )
    p = argparse.ArgumentParser(description="测试 OpenAI 兼容接口模型耗时（可模拟润色 payload）")
    p.add_argument(
        "--base-url",
        default=os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1"),
        help="兼容网关根 URL（可不含 /v1），默认环境变量 OPENAI_BASE_URL",
    )
    p.add_argument(
        "--api-key",
        default=os.environ.get("OPENAI_API_KEY", ""),
        help="默认环境变量 OPENAI_API_KEY",
    )
    p.add_argument(
        "--model",
        action="append",
        dest="models",
        metavar="NAME",
        help="模型名；可重复传入多个，依次测试",
    )
    p.add_argument("--max-tokens", type=int, default=512, help="最大生成 token（润色长文建议 4096）")
    p.add_argument("--temperature", type=float, default=0.7)
    p.add_argument("--timeout", type=float, default=600.0, help="单次请求超时秒数")
    p.add_argument("--prompt", default=None, help="用户消息正文（短测）；与 --prompt-file 二选一优先 prompt")
    p.add_argument(
        "--prompt-file",
        default=None,
        help="用户消息来源文件；polish-debug 导出默认去掉头部，仅 --- 后与线上一致",
    )
    p.add_argument(
        "--keep-polish-header",
        action="store_true",
        help="polish-debug 文件不去掉头部的快照说明，整文件作为 user（非线上行为）",
    )
    p.add_argument("--system", default=None, help="可选 system 文案（会与 --system-file 合并）")
    p.add_argument("--system-file", default=None, dest="system_file", help="可选 system 从文件读取")
    p.add_argument(
        "--messages-json",
        default=None,
        dest="messages_json",
        help="指定则忽略 prompt/--prompt-file/system，直接使用 JSON 数组作为 messages",
    )
    p.add_argument("--repeat", type=int, default=1, help="每个模型连续请求次数（取平均）")
    args = p.parse_args()

    if not args.api_key.strip():
        print("错误: 未设置 API Key（--api-key 或环境变量 OPENAI_API_KEY）", file=sys.stderr)
        sys.exit(2)

    models = args.models or []
    if not models:
        print("错误: 请至少指定一个 --model", file=sys.stderr)
        sys.exit(2)

    if args.messages_json:
        try:
            messages = load_messages_json(args.messages_json)
        except (OSError, ValueError, json.JSONDecodeError) as e:
            print(f"错误: 读取 messages-json 失败: {e}", file=sys.stderr)
            sys.exit(2)
        messages = [
            {"role": str(m["role"]), "content": str(m["content"])} for m in messages
        ]
    else:
        user_payload = load_user_payload(
            args.prompt,
            args.prompt_file,
            default_prompt,
            keep_polish_header=args.keep_polish_header,
        )
        messages = build_messages(user_payload, args.system, args.system_file)

    url = chat_completions_url(args.base_url)

    print(f"POST {url}")
    print(summarize_messages(messages))
    print(f"max_tokens={args.max_tokens} temperature={args.temperature}")
    if args.prompt_file and not args.messages_json:
        mode = "full_file_as_user" if args.keep_polish_header else "after---_same_as_java"
        print(f"prompt_file_mode={mode} path={args.prompt_file}")
    print("-" * 60)

    for model in models:
        times: list[float] = []
        last_err: str | None = None
        for i in range(max(1, args.repeat)):
            elapsed, status, comp_len, err = run_once(
                url,
                args.api_key.strip(),
                model,
                messages,
                args.max_tokens,
                args.temperature,
                args.timeout,
            )
            times.append(elapsed)
            last_err = err
            tag = f"  [{i + 1}/{args.repeat}]" if args.repeat > 1 else ""
            if err:
                print(f"model={model}{tag}  FAIL  {elapsed:.2f}s  status={status}  err={err}")
            else:
                print(
                    f"model={model}{tag}  OK  {elapsed:.2f}s  HTTP={status}  reply_chars≈{comp_len}"
                )

        if args.repeat > 1 and times:
            avg = sum(times) / len(times)
            print(f"  → average {avg:.2f}s over {len(times)} runs")
            if last_err:
                print(f"  → last error: {last_err}")
        print("-" * 60)


if __name__ == "__main__":
    main()
