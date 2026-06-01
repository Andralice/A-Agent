# A-Agent 后端 API 总表 v2.0

> **Base URL**: `http://<host>:8080` | **模型**: `claude-sonnet-4-6` | **最后修订**: 2026-05-25

---

## 目录

1. [快速开始](#1-快速开始)
2. [认证与用户系统](#2-认证与用户系统-auth)
3. [小说 CRUD](#3-小说-crud-novel)
4. [章节与大纲生成](#4-章节与大纲生成)
5. [流水线与文风参数](#5-流水线与文风参数)
6. [叙事质量与分析](#6-叙事质量与分析) ← **NEW v2.0**
7. [故事契约与审计](#7-故事契约与审计) ← **NEW v2.0**
8. [写作知识库](#8-写作知识库) ← **NEW v2.0**
9. [文风分流入口](#9-文风分流入口-style)
10. [QQ 模块](#10-qq-模块-qq)
11. [前端联调手册](#11-前端联调手册)
12. [文风参数参考](#12-文风参数参考-writingstyleparams)
13. [附录](#13-附录)

---

## 1. 快速开始

### 1.1 当前部署配置

| 项目 | 值 |
|------|-----|
| 端口 | `8080` |
| 模型 | `claude-sonnet-4-6` (via `api.meai.cloud`) |
| 默认流水线 | `LIGHT_NOVEL` |
| JWT 安全 | `enabled: true` |
| 叙事引擎 | `enabled: true` |

### 1.2 前端接入步骤

1. `GET /api/novel/config/frontend-runtime` → 获取完整开关状态
2. `POST /api/auth/login` `{"username":"admin","password":"NovelAgent#Admin2026"}` → 获取 token
3. `GET /api/novel/list` → 拉书列表（需 JWT 时带 `Authorization: Bearer <token>`）
4. `POST /api/novel/create` 或 `POST /api/novel-style/light-novel/create` → 创建小说
5. `POST /api/novel/{id}/continue` → 续写，轮询 `GET /api/novel/tasks/{taskId}`

### 1.3 通用约定

- **写操作响应** (Map 形态): `{ "status":"success", "code":"OK", "message":"..." }`
- **失败响应**: `{ "status":"error", "code":"ERROR_CODE", "message":"简短中文" }`
- **鉴权头**: `Authorization: Bearer <token>` (启用 `app.security.enabled` 时)
- **书库 404 策略**: 非公开小说对访客返回 404 (不泄露存在性)

### 1.4 运行时配置参考

`GET /api/novel/config/frontend-runtime` 返回示例:

```json
{
  "securityEnabled": true,
  "narrativeEngineEnabled": true,
  "m7ArtifactEnabled": true,
  "m9CrosscutEnabled": true,
  "characterCastEnabled": true,
  "characterStateInjectEnabled": true,
  "characterStateDeltaEnabled": true,
  "outlineTwoPhaseGraphEnabled": true,
  "outlineGraphPhaseMaxTokens": 4096,
  "autoContinueDefaultTarget": 20,
  "autoContinueMaxTarget": 200,
  "outlineDetailedPrefixChaptersDefault": 40,
  "outlineMinRoadmapChaptersDefault": 120
}
```

### 1.5 当前流水线开关状态

**开启**: 文笔润色, M3 Lint, M4 承接, M7 侧车, M9 跨章快照, 阻力层, 轻小说章前规划
**关闭**: 流体润滑 Pass, Lint 窄幅修订, M8 批评重写, 终稿去 AI 味

---

## 2. 认证与用户系统 `/api/auth`

三级权限：管理员 (全部) > 普通用户 (自己+公开) > 访客 (仅公开)

### 2.1 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/register` | 注册 `{username, password}` |
| `POST` | `/api/auth/login` | 登录 → `{token, userId, role, ip, location}` |
| `GET` | `/api/auth/me` | 当前会话 `{securityEnabled, authenticated, userId, username, role}` |
| `GET` | `/api/auth/users` | 用户列表（仅 ADMIN） |

### 2.2 书库三级权限

| 调用者 | `GET /list` 返回 | 非公开小说 `GET /{id}` |
|--------|-----------------|----------------------|
| 管理员 | 全部 | 正常返回 |
| 普通用户 | 自己的+公开 | 别人非公开 → 404 |
| 访客 | 仅公开 | 非公开 → 404 |

---

## 3. 小说 CRUD `/api/novel`

### 3.1 端点速查

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/list` | 小说列表（按角色过滤） |
| `GET` | `/api/novel/{id}` | 小说详情（含 `writingStyleParams`, `narrativeCarryover` 等） |
| `POST` | `/api/novel/create` | 创建小说并异步生成大纲+角色+首章 |
| `GET` | `/api/novel/{id}/delete-guard` | 删除前确认信息 `{title, requiredPhrase, activeTaskCount}` |
| `POST` | `/api/novel/{id}/delete` | 永久删除（需 `confirmTitle, typedPhrase, acknowledgeIrreversible`） |
| `POST` | `/api/novel/{id}/library-visibility` | 切换公开/仅管理员（需 ADMIN） |
| `GET` | `/api/novel/config/outline-plan-defaults` | 大纲规划默认值 |
| `GET` | `/api/novel/config/frontend-runtime` | 运行时配置快照 |

### 3.2 创建小说

```json
POST /api/novel/create
{
  "topic": "转生成为修仙界魔剑",
  "generationSetting": "可选，用户补充设定",
  "hotMemeEnabled": false,
  "writingStyleParams": { "dialogueRatioHint": "medium" },
  "serializationPlatform": "起点",
  "creatorNote": "上架连载，都市修仙向",
  "outlineDetailedPrefixChapters": 40,
  "outlineMinRoadmapChapters": 120
}
→ { "status":"success", "novelId": 1, "taskId": 10, "taskType": "INITIAL_BOOTSTRAP" }
```

### 3.3 删除小说

```json
GET /api/novel/{id}/delete-guard
→ { "title":"某某修仙传", "requiredPhrase":"我确认永久删除此书", "activeTaskCount":1 }

POST /api/novel/{id}/delete
{ "confirmTitle":"某某修仙传", "typedPhrase":"我确认永久删除此书", "acknowledgeIrreversible":true }
→ { "status":"success", "code":"OK" }
```

---

## 4. 章节与大纲生成

### 4.1 章节

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/chapters` | 章节列表 |
| `POST` | `/api/novel/{id}/continue` | 续写单章（异步） |
| `POST` | `/api/novel/{id}/auto-continue` | 自动续写到目标章（异步） |
| `POST` | `/api/novel/{id}/chapters/{n}/regenerate` | 单章重生（异步） |
| `POST` | `/api/novel/{id}/chapters/regenerate-range` | 区间重生（异步） |
| `PUT` | `/api/novel/{id}/chapters/{n}/content` | 编辑章节（自动存档旧版） |
| `GET` | `/api/novel/{id}/chapters/{n}/revisions` | 版本列表 |
| `GET` | `/api/novel/{id}/chapters/{n}/revisions/{revId}` | 版本详情 |
| `POST` | `/api/novel/{id}/chapters/{n}/revisions/{revId}/restore` | 回滚到指定版本 |

### 4.2 大纲

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/outline` | 获取大纲 + `outlineGraphJson` |
| `POST` | `/api/novel/{id}/outline/regenerate` | 重新生成大纲（同步，耗时长） |
| `PUT` | `/api/novel/{id}/outline/content` | 编辑大纲（自动存档） |
| `GET` | `/api/novel/{id}/outline/revisions` | 大纲版本列表 |
| `GET` | `/api/novel/{id}/outline/revisions/{revId}` | 大纲版本详情 |
| `POST` | `/api/novel/{id}/outline/revisions/{revId}/restore` | 回滚大纲 |

### 4.3 大纲冲突图谱 (`outlineGraphJson`)

两阶段大纲成功时，`outlineGraphJson` 包含结构化 `cast` (角色表: `name/role/want/fear/knowledge/summary`)、`conflicts`、`tension_matrix` 等。

### 4.4 角色设定

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/characters` | 角色列表 |
| `POST` | `/api/novel/{id}/characters/repair` | 异步修复/重建角色 |
| `PUT` | `/api/novel/{id}/characters/{cid}/content` | 编辑角色（自动存档） |
| `GET` | `/api/novel/{id}/characters/{cid}/revisions` | 版本列表 |
| `POST` | `/api/novel/{id}/characters/{cid}/revisions/{revId}/restore` | 回滚角色 |

### 4.5 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/generation-tasks` | 任务列表 |
| `GET` | `/api/novel/tasks/{taskId}` | 任务详情 |
| `POST` | `/api/novel/tasks/{taskId}/cancel` | 取消任务 |
| `POST` | `/api/novel/tasks/{taskId}/kick` | 手动触发 PENDING |
| `POST` | `/api/novel/tasks/{taskId}/retry` | 重试失败任务 |

### 4.6 监控与导出

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/writing-monitor` | 写作监控聚合（推荐轮询） |
| `GET` | `/api/novel/{id}/progress` | 进度摘要 |
| `GET` | `/api/novel/{id}/regeneration-tasks` | 活跃重生任务 |
| `GET` | `/api/novel/{id}/creative-process` | 创作全过程时间线 |
| `GET` | `/api/novel/{id}/export` | 导出为纯文本 |
| `GET` | `/api/novel/{id}/export-health` | 导出前健康检查 |

### 4.7 生成日志与事实

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/generation-logs` | 生成日志 |
| `GET` | `/api/novel/{id}/consistency-alerts` | 一致性告警 |
| `GET` | `/api/novel/{id}/chapter-facts` | 章节事实记忆 |
| `GET` | `/api/novel/{id}/chapter-sidecar` | 章节 sidecar 聚合 |
| `GET` | `/api/novel/{id}/plot-snapshots` | 阶段主线快照 |
| `GET` | `/api/novel/{id}/narrative-artifacts` | M7 叙事侧车列表 |
| `GET` | `/api/novel/{id}/chapters/{n}/narrative-artifact` | M7 单章侧车 |
| `GET` | `/api/novel/{id}/narrative-state` | M9 跨章叙事状态 |
| `GET` | `/api/novel/{id}/character-narrative-states` | 角色动态状态 |

### 4.8 续写/重生 Body 参考

```json
POST /api/novel/{id}/continue
{ "chapterNumber": 12, "generationSetting": "本章附加设定" }
→ { "taskId": 20, "taskType": "CONTINUE_SINGLE", "targetChapter": 12 }

POST /api/novel/{id}/auto-continue
{ "targetChapterCount": 80, "generationSetting": "批次设定" }
→ { "taskId": 21, "taskType": "AUTO_CONTINUE_RANGE", "rangeFrom": 13, "rangeTo": 80 }

POST /api/novel/{id}/chapters/{n}/regenerate
{ "generationSetting": "重生附加设定" }
→ { "taskId": 22, "taskType": "REGENERATE_RANGE", "targetChapter": 5 }

POST /api/novel/{id}/chapters/regenerate-range
{ "startChapter": 12, "endChapter": 20, "generationSetting": "区间重生设定" }
→ { "taskId": 23, "taskType": "REGENERATE_RANGE", "startChapter": 12, "endChapter": 20 }
```

---

## 5. 流水线与文风参数

### 5.1 流水线端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/pipeline` | 获取流水线 + `writingStyleParams` |
| `POST` | `/api/novel/{id}/pipeline` | 切换流水线 `{"pipeline":"light-novel"}` |
| `POST` | `/api/novel/{id}/hot-meme` | 热梗开关 `{"enabled":true}` |
| `POST` | `/api/novel/{id}/writing-style-params` | 更新文风参数（整树写回） |
| `POST` | `/api/novel/{id}/book-meta` | 更新书籍备注 |

### 5.2 流水线枚举与别名

| 枚举值 | 推荐别名 (kebab-case) | 其他别名 | 说明 |
|--------|----------------------|----------|------|
| `POWER_FANTASY` | `power-fantasy` | `pf`, `shuang`, `爽文` | 默认爽文 |
| `LIGHT_NOVEL` | `light-novel` | `ln`, `light` | 轻小说 |
| `SLICE_OF_LIFE` | `slice-of-life` | `daily`, `slice` | 日常向 |
| `PERIOD_DRAMA` | `period-drama` | `年代`, `年代文` | 年代文 |
| `VULGAR` | `vulgar` | `rough`, `粗俗` | 粗俗风 |

> 未匹配的任意字符串静默回退为 `POWER_FANTASY`。

### 5.3 `writingStyleParams` 更新规则

- `POST /api/novel/{id}/writing-style-params` → 整树校验后写回
- 前端须 `JSON.parse` 现有值 → 深合并编辑 → `JSON.stringify` 提交
- 空体或 `{}` 表示清空
- 根对象不含任何受支持字段时返回 `INVALID_STYLE_PARAMS`

---

## 6. 叙事质量与分析 ← NEW v2.0

### 6.1 三股线节奏

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/strand-stats` | 三线统计 + 建议 |

**返回字段**: `questCount`, `fireCount`, `constellationCount`, `lastQuestChapter`, `lastFireChapter`, `lastConstellationChapter`, `currentDominant` (QUEST/FIRE/CONSTELLATION), `chaptersSinceSwitch`, `suggestion`, `warning`

**硬约束**: Quest 不连续超 5 章 | Fire 不超 10 章不出现 | Constellation 不超 15 章不出现

### 6.2 伏笔管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/foreshadowing` | 伏笔列表 |
| `GET` | `/api/novel/{id}/foreshadowing/gantt` | 甘特图数据 |
| `POST` | `/api/novel/{id}/foreshadowing` | 手动添加伏笔 |
| `PUT` | `/api/novel/{id}/foreshadowing/{fid}` | 更新伏笔状态 |

**伏笔字段**: `id`, `content`, `loopType` (CRISIS/MYSTERY/DESIRE/EMOTION/CHOICE), `urgency` (low/medium/high/critical), `status` (PLANTED/REMINDED/PAID_OFF/ABANDONED), `plantedChapter`, `payoffChapter`, `deadlineChapter`

**添加伏笔**:
```json
POST /api/novel/{id}/foreshadowing
{ "content": "主角在藏经阁发现灵根置换术残页", "loopType": "MYSTERY", "urgency": "medium", "plantedChapter": 38, "deadlineChapter": 80 }
→ { "id": 5, "status": "success" }
```

**更新状态**:
```json
PUT /api/novel/{id}/foreshadowing/{fid}
{ "action": "payoff", "chapterNumber": 65 }
→ { "status": "success" }
```

### 6.3 阅读力分类法

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/reading-power` | 各章阅读力指标 |
| `GET` | `/api/novel/{id}/reading-power/trend` | 趋势数据（同指标，用于图表） |

**返回字段**: `chapterNumber`, `hookType`, `hookStrength` (strong/medium/weak), `coolPointPattern`, `microPayoffCount`, `hasHardViolations`, `readerDebt`

**钩子类型**: CRISIS (危机钩) / MYSTERY (悬念钩) / DESIRE (渴望钩) / EMOTION (情绪钩) / CHOICE (选择钩)
**爽点模式**: FLEX_AND_COUNTER (装逼打脸) / UNDERDOG_REVEAL (扮猪吃虎) / UNDERDOG_VICTORY (越级反杀) / AUTHORITY_CHALLENGE (打脸权威) / VILLAIN_DOWNFALL (反派翻车) / SWEET_SURPRISE (甜蜜超预期) / MISUNDERSTANDING_ELEVATION (迪化误解) / IDENTITY_REVEAL (身份掉马)

### 6.4 AI 味检测

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/chapters/{n}/ai-flavor-report` | 单章 5 维 AI 味报告 |

**5 个维度**:

| 维度 | 检测内容 | 严重度 |
|------|---------|--------|
| **词汇层** `vocabulary` | 高频AI词汇、万能副词密度、神态模板 | 密集=high, 个别=medium |
| **句式层** `sentence` | 总结句收尾、同构句、戏剧性反讽 | high/medium |
| **叙事层** `narrative` | 安全着陆、展示后解释、节奏均匀 | high/medium |
| **情感层** `emotion` | 标签化情绪、即时切换、统一反应模板 | 标签化=high, 其他=medium |
| **对话层** `dialogue` | 信息宣讲、全员书面语 | 信息宣讲=high, 其他=medium |

**返回格式**:
```json
{
  "issues": [
    { "severity": "high", "category": "ai_flavor", "subCategory": "vocabulary",
      "description": "高频万能副词", "evidence": "全文共12处（密度2.4/500字窗口）",
      "fixHint": "删除[缓缓/淡淡/微微]等万能副词，换为具体动作描写", "blocking": true }
  ],
  "summary": "3个AI味问题：1个阻断，2个高优",
  "passed": false
}
```

**6 条反 AI 味硬规则** (注入每次生成):
1. 删段末感悟句，留余味
2. 删万能副词（缓缓/淡淡/微微），换具体动作
3. 情绪用生理反应+微动作，禁止"他感到X"
4. 对话带潜台词和意图冲突
5. 制造节奏疏密对比
6. 章末禁止安全着陆，留未解决的问题

---

## 7. 故事契约与审计 ← NEW v2.0

### 7.1 契约系统

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/contracts` | 获取完整契约树 |

**契约链路**: `MASTER_SETTING → VOLUME → CHAPTER → REVIEW → CHAPTER_COMMIT`

**契约树结构**:
```json
{
  "masterSettings": [{ "id": 1, "type": "MASTER_SETTING", "content": { "title": "...", "coreConstraints": [...] } }],
  "volumes": [...],
  "chapters": [{ "id": 3, "chapterNumber": 1, "content": { "goal": "...", "mustCoverNodes": [...], "forbiddenZones": [...] } }],
  "reviews": [...],
  "commits": [...]
}
```

### 7.2 审计链

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/novel/{id}/commit-history` | 章节 COMMIT 审计历史 |

每项含: `id`, `chapterNumber`, `revisionNumber`, `commitType` (ACCEPTED/REJECTED/PENDING), `projectionStatus`, `createTime`

---

## 8. 写作知识库 ← NEW v2.0

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/knowledge/tables` | 9 张知识表列表 |
| `GET` | `/api/knowledge/search?table=写作技法&query=战斗场景` | 关键词检索 |

**9 张知识表**: `题材与调性推理`, `裁决规则`, `人设与关系`, `写作技法`, `命名规则`, `场景写法`, `桥段套路`, `爽点与节奏`, `金手指与设定`

---

## 9. 文风分流入口 `/api/novel-style`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/novel-style/{style}/create` | 按文风创建 |
| `POST` | `/api/novel-style/{style}/{id}/continue` | 按文风续写 |

> `{style}` = 上表别名（如 `light-novel`）。续写时 URL 中 `{style}` 不覆盖数据库已存 `writingPipeline`；实际生成以 `GET .../pipeline` 为准。

---

## 10. QQ 模块 `/api/qq`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/qq/message` | QQ 消息回调入口 |
| `GET` | `/api/qq/export/{id}` | 下载 txt 导出 |
| `POST` | `/api/qq/cleanup` | 清理空小说 (Header: `X-Admin-Token`) |

---

## 11. 前端联调手册

### 11.1 页面模块与接口映射

| 页面模块 | 主要接口 |
|----------|----------|
| 登录/会话 | `GET /api/auth/me`, `POST /api/auth/login` |
| 书架 | `GET /api/novel/list` |
| 详情页头 | `GET /api/novel/{id}`, `GET .../pipeline`, `GET .../progress` |
| 大纲 Tab | `GET .../outline`, `POST .../outline/regenerate`, `PUT .../outline/content` |
| 角色面板 | `GET .../characters`, `POST .../characters/repair`, `PUT .../characters/{cid}/content` |
| 章节阅读 | `GET .../chapters`, `PUT .../chapters/{n}/content` |
| 续写操作 | `POST .../continue`, `POST .../auto-continue` |
| 重生操作 | `POST .../chapters/{n}/regenerate`, `POST .../chapters/regenerate-range` |
| 任务中心 | `GET .../generation-tasks`, `POST .../cancel/retry/kick` |
| 监控横幅 | `GET .../writing-monitor` |
| **NEW** 叙事分析 | `GET .../strand-stats`, `GET .../reading-power/trend` |
| **NEW** 伏笔面板 | `GET .../foreshadowing`, `GET .../foreshadowing/gantt` |
| **NEW** AI味报告 | `GET .../chapters/{n}/ai-flavor-report` |
| **NEW** 契约树 | `GET .../contracts` |
| **NEW** 审计链 | `GET .../commit-history` |
| 质量/事实 | `GET .../consistency-alerts`, `GET .../chapter-facts`, `GET .../chapter-sidecar` |
| 叙事引擎 | `GET .../narrative-artifacts` (M7), `GET .../narrative-state` (M9) |
| 导出 | `GET .../export`, `GET .../export-health` |

### 11.2 异步任务轮询策略

1. 触发写操作 → 获取 `taskId`
2. 轮询 `GET /api/novel/tasks/{taskId}` (间隔 2-5s)
3. 并行轮询 `GET .../writing-monitor`
4. `DONE/FAILED/CANCELLED` → 停止轮询，刷新章节列表
5. 遇 `TASK_CONFLICT` → 刷新 `generation-tasks`，禁用冲突按钮

### 11.3 角色叙事调度

**全书默认登场**: `POST .../writing-style-params` 中传 `chapterCastDefault`:
```json
{ "chapterCastDefault": { "characters": [{"name":"主角名","focus":["焦点1"]}], "restrictOthersToBackground": true } }
```

**本章覆盖**: 在 `generationSetting` 字符串中传 JSON (需 `JSON.stringify`):
```json
"generationSetting": "{\"notes\":\"对峙为主\",\"chapterCast\":{\"characters\":[{\"name\":\"主角\",\"focus\":[\"急躁\"]}]}}"
```

**动态状态**: 任务 DONE 后调 `GET .../character-narrative-states` 获取模型侧写的可变状态。

### 11.4 前端职责清单

- 拉 `frontend-runtime` 对齐 UI 开关
- `writingStyleParams` 须深合并后提交（避免覆盖丢字段）
- `pipeline`/`{style}` 使用 kebab-case 别名
- 非公开书的 404 提示"需要登录"，非"小说不存在"
- 失败只展示 `message`，不展示堆栈
- `generationSetting` 为字符串类型；结构化时 `JSON.stringify`

### 11.5 常见问题

| 问题 | 简答 |
|------|------|
| 传的 pipeline 变成爽文？ | 仅映射表内别名生效，其余回退 `POWER_FANTASY` |
| novel-style 续写仍是爽文？ | URL 中 `{style}` 不覆盖库内 `writingPipeline`；先 `POST .../pipeline` 再续写 |
| M9 状态为 null？ | 可能尚无章节落库、开关关闭或解析失败 |
| 第一章无 M9 注入？ | 预期：需上一章已落库才有快照 |
| `TASK_CONFLICT`？ | 章节区间与活跃任务重叠，或大纲重写占用中 |
| 访客看小说是 404？ | `libraryPublic=false` 时故意返回 404 |
| 角色死了还会出场？ | 服务端不维护生死状态机；需在 `generationSetting` 中约束 |

---

## 12. 文风参数参考 `writingStyleParams`

根 JSON 对象可包含以下任一分支（可并存，服务端不校验字段互斥）：

| 分支 | 根键 | 说明 |
|------|------|------|
| 文风枚举 | `styleIntensity`, `dialogueRatioHint`, `humorLevel`, `periodStrictness` | 标签式微调 |
| 叙事情绪 | `narrative` (object) | `emotionType`, `intensityMin/Max` [0,1], `suppression`, `triggerFact`, `forbidden`, `rhythmHint` 等 |
| 叙事物理 M6 | `narrativePhysicsMode` | `continuous` / `stress` |
| 认知弧线 | `narrativeArcPhase` + `cognitionArc` | `early/mid/late` + `byPhase` |
| 文笔四层 | `rhythm`, `perception`, `language`, `informationFlow` | 每个含数值 [0,1] 旋钮 |
| 全书默认登场 | `chapterCastDefault` | `characters[]` + `restrictOthersToBackground` |

**叙事引擎关闭后**: `narrative` 不注入；认知弧线与文笔四层仍生效。
**提示优先级**: 大纲事实 > 合规审核 > 管线硬约束 > 文风枚举 > 文笔微调

### 12.1 `narrative` 子字段速查

| 字段 | 类型 | 说明 |
|------|------|------|
| `emotionType` | string | 情绪类型标签 |
| `intensityMin` / `intensityMax` | number [0,1] | 强度带宽 |
| `suppression` | number [0,1] | 压抑度 |
| `triggerFact` | string | 触发锚点 |
| `forbidden` | string[] | 禁止短语 |
| `rhythmHint` / `textureHint` / `povHint` | string | 节奏/材质/视角意图 |
| `affection` / `awkwardness` / `assertiveness` | number [0,1] | 轻小说三维 |
| `interactionFocus` | string | 互动写法焦点 |
| `readerInferenceRule` | boolean | 强调推断 |

### 12.2 文笔四层旋钮

**`rhythm`**: `sentenceLengthVariance`, `pauseDensity`, `fragmentation` [0,1]
**`perception`**: `sensoryWeight`, `conceptualWeight`, `externalActionWeight`, `internalThoughtWeight` [0,1]
**`language`**: `abstractionLevel`, `wordPrecision`, `adjectiveControl`, `technicalDensity` [0,1]
**`informationFlow`**: `revealType` (immediate/layered/withheld), `uncertaintyMaintenance`, `clarityDelay` [0,1]

---

## 13. 附录

### 13.1 统一错误码

| 错误码 | 含义 |
|--------|------|
| `OK` | 成功 |
| `INVALID_ARGUMENT` | 参数不合法 |
| `CREATE_TASK_FAILED` / `CONTINUE_TASK_FAILED` / `AUTO_CONTINUE_TASK_FAILED` | 任务创建失败 |
| `REGENERATE_TASK_FAILED` / `REGENERATE_RANGE_TASK_FAILED` | 重生任务失败 |
| `TASK_CONFLICT` | 区间锁冲突/大纲占用 |
| `TASK_NOT_FOUND` / `TASK_CANCEL_FAILED` / `TASK_RETRY_FAILED` / `TASK_KICK_FAILED` | 任务操作失败 |
| `OUTLINE_REGENERATE_FAILED` | 大纲重写失败 |
| `PIPELINE_UPDATE_FAILED` / `STYLE_PARAMS_UPDATE_FAILED` / `BOOK_META_UPDATE_FAILED` / `HOT_MEME_UPDATE_FAILED` | 配置更新失败 |
| `INVALID_STYLE_PARAMS` | 文风参数 JSON 无效 |
| `STYLE_CREATE_TASK_FAILED` / `STYLE_CONTINUE_TASK_FAILED` | 文风分流失败 |
| `CHARACTER_REPAIR_TASK_FAILED` | 角色修复失败 |
| `DELETE_CONFIRM_MISMATCH` | 删除确认不匹配 |
| `AUTH_DISABLED` | 安全未启用 |
| `UNAUTHORIZED` | 用户名/密码错误 (401) |
| `FORBIDDEN` | 需要管理员 (403) |

### 13.2 `TASK_CONFLICT` 触发规则

- 目标小说存在 `PENDING/RUNNING` 且区间重叠的任务 → 拒绝
- 重新生成大纲 (`OUTLINE_REGENERATE`) 与章节类任务互斥
- 数据库级任务锁跨实例生效

### 13.3 叙事引擎里程碑 (M1-M10)

| 里程碑 | 说明 | 当前状态 |
|--------|------|---------|
| M1 | 叙事 Profile 注入初稿 | 开启 |
| M2 | 章前 Planner (叙事脚本) | 开启 (轻小说走自有的章规划器) |
| M3 | Lint 扫描 | 开启 (修复关闭) |
| M4 | 跨章承接 | 开启 |
| M5 | 情绪波形/句法力学 | 开启 |
| M6 | 叙事物理模式 | 开启 |
| M7 | 侧车 Artifact 落库 | 开启 |
| M8 | 批评+重写 | 关闭 |
| M9 | 跨章快照 | 开启 |
| M10 | 预留 | - |

### 13.4 生成质量优化记录 (2026-05-21)

- **叙述者人格注入**: 每章初稿最前面注入叙述者人格声明
- **主角驱动力**: 每章强制主角主动做出至少一个选择并产生可见后果
- **参数体系精简**: 移除文笔四层数值和认知弧线数值
- **文风呼吸感**: 润色指令改为"仅删真正的重复，保留场面描写和感官细节"
- **防名字污染**: 三道防线（prompt + cast 校验正则 + normalizeName）

### 13.5 `generationSetting` 字符串约定

- **纯文本模式**: `"本章希望节奏紧一点"` → 整段为本章说明
- **结构化模式** (推荐): `"{\"notes\":\"对峙为主\",\"chapterCast\":{...}}"` → `JSON.stringify` 后传入
- **勿**: 以 `{` 开头却非合法 JSON (易解析失败)

### 13.6 角色生死与后续出场

- 服务端**不维护**存活/死亡状态机
- 已故角色仍保留在档案与锁定列表中，后续章可能被模型写出
- 约束方法: 在 `generationSetting` 中明确禁忌，或重生后续章

---

> **相关文档**: [架构.md](./架构.md) | [系统功能说明.md](./系统功能说明.md) | [导演台与叙事功能使用说明.md](./导演台与叙事功能使用说明.md) | [前端与用户指引.md](./前端与用户指引.md) | [db.md](./db.md)
