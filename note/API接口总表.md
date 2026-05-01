# A-Agent 后端 API 总表

本文档按当前代码整理，供前端直接对接使用。

## 基础信息

- 基础地址: `http://<host>:8080`
- 主要模块:
  - 小说管理: `/api/novel`
  - QQ 回调/管理: `/api/qq`
- 通用返回风格:
  - 异步触发类接口通常返回:
    - `{"status":"success","message":"...已启动..."}`
    - `{"status":"error","message":"错误信息"}`
  - 现在统一增加 `code` 字段:
    - 成功: `code = "OK"`
    - 失败: 例如 `INVALID_ARGUMENT`、`REGENERATE_RANGE_TASK_FAILED` 等

---

## 统一错误码（当前）

- `OK`: 请求受理成功
- `INVALID_ARGUMENT`: 参数不合法/缺失
- `CREATE_TASK_FAILED`: 创建任务失败
- `CONTINUE_TASK_FAILED`: 续写任务失败
- `AUTO_CONTINUE_TASK_FAILED`: 自动续写任务失败
- `REGENERATE_TASK_FAILED`: 单章重生任务失败
- `REGENERATE_RANGE_TASK_FAILED`: 区间重生任务失败
- `PIPELINE_UPDATE_FAILED`: 流水线更新失败
- `STYLE_CREATE_TASK_FAILED`: 文风创建任务失败
- `STYLE_CONTINUE_TASK_FAILED`: 文风续写任务失败
- `CHARACTER_REPAIR_TASK_FAILED`: 角色修复任务失败
- `TASK_CONFLICT`: 目标章节或区间已有进行中的重生/续写任务

前端建议：
- `status=success && code=OK` 视为成功
- 其他情况全部走错误流程，优先展示 `message`
- 若 `code=TASK_CONFLICT`，提示用户稍后重试，并刷新 `regeneration-tasks`

## AI输出结构化策略（已执行）

- 角色设定链路已改为 **JSON-only 严格模式**（治本项）：
  - 生成端强制仅输出 JSON 对象（`characters` 数组）
  - 服务端严格验收（字段校验+黑名单过滤）
  - 解析失败会自动重试，不再把自由文本当成功角色数据入库
- 章节正文链路仍保持文本（为了写作自然度），但继续使用：
  - 角色强锁
  - 事实表
  - 快照漂移检测
  - 自动修复
  - 章节 sidecar 结构提取（title/facts/continuity_anchor）供程序消费

## 1) 小说管理接口 `/api/novel`

### 1. 获取小说列表
- 方法: `GET`
- 路径: `/api/novel/list`
- 作用: 获取全部小说基础信息
- 请求参数: 无
- 前端调用示例:
```json
{}
```

### 2. 获取小说基础信息
- 方法: `GET`
- 路径: `/api/novel/{novelId}`
- 作用: 查询单本小说详情（标题/题材/设定/时间等）
- 路径参数:
  - `novelId` (Long, 必填)

### 3. 获取章节列表
- 方法: `GET`
- 路径: `/api/novel/{novelId}/chapters`
- 作用: 获取该小说全部章节
- 路径参数:
  - `novelId` (Long, 必填)

### 4. 获取大纲
- 方法: `GET`
- 路径: `/api/novel/{novelId}/outline`
- 作用: 获取大纲与大纲完成状态
- 路径参数:
  - `novelId` (Long, 必填)
- 返回关键字段:
  - `outline`
  - `ready` (Boolean)
  - `globalSetting`

### 5. 获取当前流水线
- 方法: `GET`
- 路径: `/api/novel/{novelId}/pipeline`
- 作用: 获取该小说当前使用的文风流水线
- 返回示例:
```json
{
  "novelId": 1,
  "pipeline": "LIGHT_NOVEL"
}
```

### 6. 更新流水线（后续章节生效）
- 方法: `POST`
- 路径: `/api/novel/{novelId}/pipeline`
- 作用: 切换后续章节生成风格（不改历史章节）
- Body(JSON):
```json
{
  "pipeline": "light"
}
```
- 支持值:
  - `power_fantasy` / 其他默认值
  - `light` / `light-novel` / `ln`
  - `slice` / `slice-of-life` / `daily`

### 7. 获取角色设定
- 方法: `GET`
- 路径: `/api/novel/{novelId}/characters`
- 作用: 获取角色档案列表

### 8. 修复角色设定（缺失/脏数据）
- 方法: `POST`
- 路径: `/api/novel/{novelId}/characters/repair`
- 作用:
  - 自动修复角色表脏数据（未知角色、乱码符号）
  - 若角色设定缺失，可自动补全生成
  - 支持强制重建
- Body(JSON):
```json
{
  "forceRegenerate": false,
  "rebuildMode": "all",
  "characterContextHint": "主角沈渊与弟子修筠是师徒关系，沈渊为第一任持剑主",
  "targetCharacterNames": ["沈渊", "修筠"],
  "extraHint": "强调师徒关系冲突线，避免改名"
}
```
- 字段说明:
  - `forceRegenerate` (Boolean, 可选，默认 false)
    - `false`: 有可用角色时仅修复；无可用角色时自动补全
    - `true`: 忽略现有角色，强制重新生成并覆盖
  - `rebuildMode` (String, 可选，默认 `all`)
    - `all`: 全量重建角色档案
    - `partial`: 部分重建，仅更新 `targetCharacterNames` 指定角色
    - 其他值会直接返回 `INVALID_ARGUMENT`
  - `characterContextHint` (String, 可选)
    - 可输入任意角色相关上下文（关系、身份、设定约束、关键背景）
  - `firstSwordMaster` (String, 可选，兼容旧字段)
    - 已兼容但不再推荐；建议统一使用 `characterContextHint`
  - `targetCharacterNames` (String[], 可选)
    - `partial` 模式下生效，指定要重建的角色名
  - `extraHint` (String, 可选)
    - 前端可传额外修复要求（风格、关系、设定重点等）
- 生成参考策略（后端自动）:
  - 会参考该小说现有大纲
  - 会参考已生成章节摘要
  - 会参考现有角色档案，尽量保持名称与关系连续

### 9. 获取生成日志
- 方法: `GET`
- 路径: `/api/novel/{novelId}/generation-logs`
- 作用: 查看大纲/角色/章节生成日志

### 10. 获取一致性告警
- 方法: `GET`
- 路径: `/api/novel/{novelId}/consistency-alerts`
- 作用: 查看角色名一致性等告警记录

### 11. 获取章节事实表
- 方法: `GET`
- 路径: `/api/novel/{novelId}/chapter-facts`
- 作用: 获取章节事实记忆（角色状态/章节钩子）

### 12. 获取章节 sidecar 视图
- 方法: `GET`
- 路径: `/api/novel/{novelId}/chapter-sidecar`
- 作用: 按章节聚合返回结构化 sidecar（关键事实 + 衔接锚点）
- 返回示例:
```json
[
  {
    "chapterNumber": 12,
    "chapterTitle": "第12章 暗潮",
    "facts": ["主角确认线索来源于旧档案", "女主决定隐瞒关键真相"],
    "continuityAnchor": "结尾主角决定次日前往旧城区，与下一章开场一致"
  }
]
```

### 13. 获取阶段主线快照
- 方法: `GET`
- 路径: `/api/novel/{novelId}/plot-snapshots`
- 作用: 获取每 5 章自动生成的阶段主线快照（用于防跑偏）

### 14. 获取进度
- 方法: `GET`
- 路径: `/api/novel/{novelId}/progress`
- 作用: 获取当前创作进度汇总
- 返回关键字段:
  - `status` 固定为 `success`（接口调用状态）
  - `code` 固定为 `OK`
  - `generationStatus` 业务进度状态（`generating` / `generating_or_ready` / `failed`）
  - `outlineReady`
  - `charactersReady`
  - `chapterCount`
  - `failedCount`

### 15. 获取创作全过程
- 方法: `GET`
- 路径: `/api/novel/{novelId}/creative-process`
- 作用: 返回小说+角色+章节+日志+时间线的聚合视图

### 16. 新建小说
- 方法: `POST`
- 路径: `/api/novel/create`
- 作用: 发起“题材 -> 大纲 -> 角色 -> 初始章节”异步生成
- Body(JSON):
```json
{
  "topic": "转生成为修仙界魔剑",
  "generationSetting": "可选，用户补充设定"
}
```
- 字段说明:
  - `topic` (String, 必填)
  - `generationSetting` (String, 可选)

### 17. 续写章节
- 方法: `POST`
- 路径: `/api/novel/{novelId}/continue`
- 作用: 续写下一章或指定章
- Body(JSON):
```json
{
  "chapterNumber": 12,
  "generationSetting": "可选，本次续写附加设定"
}
```
- 字段说明:
  - `chapterNumber` (Integer, 可选，不传=自动下一章)
  - `generationSetting` (String, 可选)

### 18. 自动续写到目标章节
- 方法: `POST`
- 路径: `/api/novel/{novelId}/auto-continue`
- 作用: 从当前章节自动续写到目标章节
- Body(JSON):
```json
{
  "targetChapterCount": 80,
  "generationSetting": "可选，批次设定"
}
```
- 字段说明:
  - `targetChapterCount` (Integer, 可选)
    - 不传时使用后端配置 `novel.auto-continue.default-target`
    - 不能超过 `novel.auto-continue.max-target`
  - `generationSetting` (String, 可选)

### 19. 重生某一章
- 方法: `POST`
- 路径: `/api/novel/{novelId}/chapters/{chapterNumber}/regenerate`
- 作用: 重新生成指定章节
- Body(JSON):
```json
{
  "generationSetting": "可选，本章重生附加设定"
}
```

### 20. 导出小说（文本）
- 方法: `GET`
- 路径: `/api/novel/{novelId}/export`
- 作用: 返回整本小说 txt 内容（字符串）

### 21. 导出前体检
- 方法: `GET`
- 路径: `/api/novel/{novelId}/export-health`
- 作用: 导出前检查章节号连续性、标题重复、空章节等
- 返回关键字段:
  - `healthy` (Boolean)
  - `issues` (String[])

### 22. 区间重生章节
- 方法: `POST`
- 路径: `/api/novel/{novelId}/chapters/regenerate-range`
- 作用: 指定章节区间重生（含起止章）
- Body(JSON):
```json
{
  "startChapter": 12,
  "endChapter": 20,
  "generationSetting": "可选，本次重生附加设定"
}
```
- 说明:
  - 支持 `startChapter > endChapter`，后端会自动归一化。
  - 如果提交区间与正在重生的区间重叠，后端会拒绝该请求（防误触/重复任务）。
  - 每章重生后都会更新事实表；每 5 章节点会刷新阶段快照。

### 23. 查询区间重生任务状态
- 方法: `GET`
- 路径: `/api/novel/{novelId}/regeneration-tasks`
- 作用: 查询当前是否有重生任务在运行，以及运行中的区间
- 返回示例:
```json
{
  "novelId": 1,
  "hasRunningTask": true,
  "runningRanges": ["12-20"]
}
```

---

## 2) QQ 模块接口 `/api/qq`

### 1. QQ 消息回调入口
- 方法: `POST`
- 路径: `/api/qq/message`
- 作用: 接收 NapCat/QQ 事件并交给命令分发
- Body:
  - `String` 原始 payload（不是固定 DTO）
- 返回:
  - 成功: `{"status":"ok","retcode":0}`
  - 失败: `{"status":"failed","retcode":1,"message":"处理失败"}`

### 2. 下载导出文件
- 方法: `GET`
- 路径: `/api/qq/export/{novelId}`
- 作用: 以附件形式下载 `txt` 文件
- 路径参数:
  - `novelId` (Long, 必填)

### 3. 手动清理空小说
- 方法: `POST`
- 路径: `/api/qq/cleanup`
- 作用: 触发清理任务
- Header:
  - `X-Admin-Token: <token>`
- 返回示例:
```json
{"status":"ok","cleaned_count":3}
```
或
```json
{"status":"failed","message":"无权限"}
```

---

## 3) 文风分流接口 `/api/novel-style`

### 1. 按文风创建小说
- 方法: `POST`
- 路径: `/api/novel-style/{style}/create`
- 支持 style:
  - `light` / `light-novel` / `ln` -> 轻小说
  - `slice` / `slice-of-life` / `daily` -> 日常向
  - 其他值 -> 默认爽文
- Body(JSON):
```json
{
  "topic": "校园社团日常",
  "generationSetting": "偏轻松、对白多"
}
```

### 2. 按文风入口续写
- 方法: `POST`
- 路径: `/api/novel-style/{style}/{novelId}/continue`
- Body(JSON):
```json
{
  "chapterNumber": 12,
  "generationSetting": "本章希望偏日常互动"
}
```

> 说明：文风会持久化在小说记录中；后续从普通续写接口进入也会沿用该小说的流水线。

---

## 4) 前端对接建议（关键）

- `auto-continue` 输入建议:
  - 前端限制 `targetChapterCount > 当前章节数`
  - 前端限制 `targetChapterCount <= max-target`（当前默认 200）
- 所有异步触发接口:
  - 点击后立即提示“任务已启动”
  - 轮询 `/api/novel/{novelId}/progress` 刷新状态
- 质量监控页面可增加:
  - `consistency-alerts` 列表（看是否发生角色名漂移）
  - `chapter-facts` 列表（看连续性记忆是否正常）
  - `export-health` 一键体检结果
  - `plot-snapshots` 查看每 5 章主线快照

### 前端建议调用流（请直接按这个做）

1. **详情页初始化**
- 并发调用：
  - `GET /api/novel/{novelId}`
  - `GET /api/novel/{novelId}/progress`
  - `GET /api/novel/{novelId}/pipeline`
  - `GET /api/novel/{novelId}/regeneration-tasks`
- 页面行为：
  - 若 `hasRunningTask=true`，禁用“区间重生提交”按钮并显示 `runningRanges`

2. **切换文风流水线**
- 先读：`GET /api/novel/{novelId}/pipeline`
- 再写：`POST /api/novel/{novelId}/pipeline`
- 成功后：
  - 刷新 pipeline 标签
  - 提示“仅后续章节生效”

3. **单章续写/自动续写**
- 单章续写：`POST /api/novel/{novelId}/continue`
- 自动续写：`POST /api/novel/{novelId}/auto-continue`
- 提交后：
  - toast “任务已启动”
  - 每 2~3 秒轮询 `GET /api/novel/{novelId}/progress`

4. **区间重生（关键）**
- 提交前必须先调：`GET /api/novel/{novelId}/regeneration-tasks`
- 若 `hasRunningTask=true`：
  - 阻止提交，提示“当前重生区间: ...”
- 提交：`POST /api/novel/{novelId}/chapters/regenerate-range`
- 提交后：
  - 轮询 `regeneration-tasks`，直到 `hasRunningTask=false`
  - 再刷新：
    - `GET /api/novel/{novelId}/chapters`
    - `GET /api/novel/{novelId}/chapter-facts`
    - `GET /api/novel/{novelId}/plot-snapshots`
    - `GET /api/novel/{novelId}/progress`

5. **导出前检查**
- 调：`GET /api/novel/{novelId}/export-health`
- 若 `healthy=false`：
  - 展示 `issues` 并阻止直接导出
- 若 `healthy=true`：
  - 允许导出 `/api/qq/export/{novelId}`

6. **sidecar 可视化（建议）**
- 调：`GET /api/novel/{novelId}/chapter-sidecar`
- 用法：
  - 在章节详情右侧显示“本章关键事实 + 衔接锚点”
  - 与正文联动，帮助人工快速判断是否跑偏

---

## 5) 配置项（与前端强相关）

`application.yml`:

- `novel.auto-continue.default-target`: 自动续写默认目标章节数（未传目标时）
- `novel.auto-continue.max-target`: 自动续写安全上限（防止误触发过大量生成）

