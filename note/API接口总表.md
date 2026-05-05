# A-Agent 后端 API 总表

本文档按当前代码整理，供前端直接对接使用。

- **文档修订（2026-05-02｜晚）**：**两阶段大纲冲突图谱** `outline_graph_json` 含结构化 **`cast`**（角色表：`name`/`role`/`want`/`fear`/`knowledge`/`summary`）；**`GET /api/novel/{novelId}/outline`** 增补返回 **`outlineGraphJson`**（与实体字段同源）；**叙事引擎** 补充 **`resistance-layer-enabled`**、**`flow-pass-*`**、M8 **`tooSmooth`** 说明（§16.2.7）；**`note/push-github.ps1`** / **`note/push-github.sh`** 一键提交并推送 GitHub。
- **文档修订（2026-05-02）**：**「同源静态页「导演台」`/novels.html`」** 专节 + **HTTP 接口清单**（含导演台 **POST pipeline / hot-meme / writing-style-params**）；**「前端职责与常见问题（联调）」**；§1.3 **首章无 M9**；**`GET .../pipeline`** 增补 **`hotMemeEnabled`**、**`libraryPublic`**。
- **文档修订（2026-05-03）**：与 **Electron/web-agent** 仓库 `note/前端对接需求.md` 对齐；新增 **`GET /api/novel/config/frontend-runtime`**、**`GET /api/novel/{novelId}/narrative-state`（M9）**；文末增加 **「附录：联调 JSON 样例」**。
- **文档修订（M9 v1.1 / 导演台）**：**`narrative_state_json`** 增加 **`relationshipHints`**、**`tensionRippleHint`**、**`schemaVersion`**；**`/novels.html`** 增加 **book-meta**、**auto-continue**；附录 A.1 样例已更新。
- **与 `web-agent/note/前端对接需求.md` 的关系（重要）**：该文件位于 **Electron/web-agent 前端仓库**，由 **前端维护**。后端 **只读、不写入** 该文件；不在该文件内追记 API 变更。联调所需的 **契约说明、字段含义、别名表、错误与 JSON 样例、运行时开关、`fromPath` 全表、M7/M9 行为** 等，一律在 **本文档** 与 **`前端与用户指引.md`** 中 **撰写与修订**；若前端备忘与本文冲突，以 **本仓库当前代码 + 本文档** 为准。后端未在每次迭代中「对照检查」前端仓库文件内容，以前述两文档为权威即可。  
- **上手（M1～M10 + 导演台）**：[导演台与叙事功能使用说明.md](./导演台与叙事功能使用说明.md)。
- **文档修订（2026-05-05）**：**「角色生死与后续出场」**（剧情语义｜与 HTTP 契约并列的产品预期）：见下文 **「常见问题」** 新增一行及 **「角色生死与后续出场」** 专节。
- **文档修订（2026-05-02｜文笔与参数总表）**：**`writingStyleParams`** 存库改为 **整棵 JSON 校验后写回**（与叙事 / 认知弧线 / 文笔四层并存不丢字段）；新增 **§16.2.1～16.2.6**（文风枚举、**情绪参数 `narrative`**、**M6**、**认知弧线**、**文笔四层** 的类型、范围与前端配置说明）；**文笔旋钮**注入 **初稿 + 主润色**。
- **文档修订（2026-05-02｜参数并存与前端须知）**：新增 **§16.2.8**（无字段级互斥校验、**M2 与轻小说章规划互斥**、**叙事引擎关闭后认知/文笔仍生效**、语义重叠与提示优先级、主润色 vs Lint/Flow/M8）。

## 基础信息

- 基础地址: `http://<host>:8080`
- 主要模块:
  - 小说管理: `/api/novel`
  - 可选登录（书库权限）: `/api/auth`
  - QQ 回调/管理: `/api/qq`
- **书库访问控制（可选）**：当配置 `app.security.enabled=true` 时，HTTP 书库对「非公开」小说要求管理员 JWT；请求头携带 `Authorization: Bearer <token>`（登录接口下发）。关闭安全开关时行为与旧版一致（列表与详情不设限）。详见下文 **「0) 书库访问控制与管理员登录」**。
- 通用返回风格:
  - **小说管理 Map 形态**（`/api/novel/*`、`/api/novel-style/*` 多数写操作）:
    - 成功: `status=success`，`code=OK`，`message` 为给人读的说明
    - 失败: `status=error`，`code` 为业务错误码，`message` 为**简短中文**（不把 JDBC/堆栈/英文异常原文暴露给用户）
  - **个别接口**直接返回实体/List/String（如 `GET /api/novel/{id}`、`GET .../export`）；异常时走全局包装为 `ApiResponse`（见下）。
- **全局异常包装 `ApiResponse`**（未在上述控制器内捕获时）:
  - 字段: `code`(数字 HTTP 语义)、`message`、`data`
  - 典型: `404` 未找到小说（含：**访客访问「仅管理员」书库时故意返回 404**，避免泄露该书存在）；`400` 参数问题；`403` 需要管理员登录（如修改书库可见性）；`500` 统一为「服务暂时不可用，请稍后重试」（详情仅服务端日志）
- **`writingStyleParams` 全书 JSON**（文风 / 情绪 `narrative` / M6 / 认知弧线 / 文笔四层）的类型、范围与前端配置：**§16.2.1～16.2.6**；**并存边界与「冲突」说明（前端必读）**：**§16.2.8**。精简备忘见 **[前端与用户指引.md](./前端与用户指引.md)**「二」。

### 同源静态页「导演台」`/novels.html`（与产品前端、AI 协作）

| 条目 | 说明 |
|------|------|
| **浏览器路径** | 与 REST **同源**：**`http://<host>:8080/novels.html`**（path 为 **`/novels.html`**，**不是** `/api/...`）。 |
| **源码位置** | 本仓库 **`src/main/resources/static/novels.html`**，Spring Boot **静态资源**随 jar 下发。 |
| **作用（是什么）** | 内置 **轻量联调 + 小运维**：`frontend-runtime`、书列表、登录、创建/删除守卫、按书 **导演台**（**`GET`**：`writing-monitor`、`narrative-state`、`pipeline`、`generation-tasks`；**`POST`**：**`.../pipeline`**、**`.../hot-meme`**、**`.../writing-style-params`**、**`.../book-meta`**、**`.../continue`**、**`.../auto-continue`**；任务 **cancel / kick / retry**）。与 web-agent **同一套 REST**。 |
| **不是什么** | **不是** Electron **web-agent** 里的产品 UI；**不**替代 **`web-agent/note/前端对接需求.md`**（该文件由前端仓库维护，后端 **只读**）。契约与字段以 **本文档 + 本仓库 Java** 为准。 |
| **产品前端（另一仓库）该怎么做** | 线上/桌面产品 **仍应在 web-agent 中** 按本文档调用 **`/api/novel/...`**。`/novels.html` **可选**：用于与本服务 **对照 JSON**、联调、QA 验收；**不必**复刻或内嵌，除非团队显式用 WebView 挂同源页。 |
| **AI / 编码助手该怎么做** | 排查小说域 HTTP 时：可引导用户打开 **`http://<host>:8080/novels.html`** 复现；说明接口时 **引用本文档 § 与具体 ` /api/...` 路径**；「导演台」指 **该静态路径 + 其内部发起的 API**，勿与 **web-agent 路由** 混为一谈；**不要**把 HTML 里的展示逻辑当成唯一真相来源。 |

#### `/novels.html` 调用的 HTTP 接口清单（与源码一致）

表中 **`{novelId}`**、**`{taskId}`** 为路径数字；除登录外，凡访问非公开书或任务，与 **`GET /api/novel/list`** 相同，须在启用安全时携带 **`Authorization: Bearer <jwt>`**（见 §0）。

| 场景 | 方法 | 路径 |
|------|------|------|
| 会话探测 | `GET` | `/api/auth/me` |
| 管理员登录 | `POST` | `/api/auth/login` |
| 运行时配置条 | `GET` | `/api/novel/config/frontend-runtime` |
| 创建弹窗·大纲默认 | `GET` | `/api/novel/config/outline-plan-defaults` |
| 书列表 | `GET` | `/api/novel/list` |
| 切换书库公共/仅管理员 | `POST` | `/api/novel/{novelId}/library-visibility` |
| 新建小说 | `POST` | `/api/novel/create` |
| 删除前守卫 | `GET` | `/api/novel/{novelId}/delete-guard` |
| 永久删除 | `POST` | `/api/novel/{novelId}/delete` |
| 导演台·写作监控 | `GET` | `/api/novel/{novelId}/writing-monitor` |
| 导演台·M9 跨章状态 | `GET` | `/api/novel/{novelId}/narrative-state` |
| 导演台·流水线快照 | `GET` | `/api/novel/{novelId}/pipeline` |
| 导演台·切换流水线 | `POST` | `/api/novel/{novelId}/pipeline` |
| 导演台·热梗开关 | `POST` | `/api/novel/{novelId}/hot-meme` |
| 导演台·文风微参 | `POST` | `/api/novel/{novelId}/writing-style-params` |
| 导演台·任务列表 | `GET` | `/api/novel/{novelId}/generation-tasks` |
| 导演台·取消任务 | `POST` | `/api/novel/tasks/{taskId}/cancel` |
| 导演台·唤醒 PENDING | `POST` | `/api/novel/tasks/{taskId}/kick` |
| 导演台·重试失败/已取消 | `POST` | `/api/novel/tasks/{taskId}/retry` |
| 导演台·续写一章 | `POST` | `/api/novel/{novelId}/continue` |
| 导演台·自动续写到目标章 | `POST` | `/api/novel/{novelId}/auto-continue` |
| 导演台·书籍备注（展示用） | `POST` | `/api/novel/{novelId}/book-meta` |

---

## 前端职责与常见问题（联调）

本节写清 **应由前端实现/遵守的约定**，以及联调时 **高频问题的一问一答**；与 **`web-agent/note/前端对接需求.md`** 的关系见文首 **「与前端对接需求的关系」**——该文件 **只读**，契约以 **本文档 + [前端与用户指引.md](./前端与用户指引.md)** 为准。

### 应由前端实现的事项（清单）

| 事项 | 说明 / 接口 |
|------|----------------|
| **拉运行时配置** | 应用启动或进入创作域时调用 **`GET /api/novel/config/frontend-runtime`**，用返回的 **`m9CrosscutEnabled`**、`app.security.enabled` 等与 UI 开关、文案、校验对齐（部署覆盖 `application.yml` 时以本接口为准）。 |
| **书库与安全** | `app.security.enabled=true` 时：登录 **`POST /api/auth/login`** 存 JWT；访问 **`libraryPublic=false`** 的书须在受保护接口上带 **`Authorization: Bearer <token>`**；理解访客对非公开书 **`GET /api/novel/{id}` 等为 404**（故意不泄露存在性）。 |
| **异步任务 UX** | `create` / `continue` / `regenerate` 等返回 **`taskId`** 后：轮询 **`GET /api/novel/{novelId}/writing-monitor`**（推荐）或 **`GET /api/novel/tasks/{taskId}`**；按需暴露 **`cancel` / `retry` / `kick`**。 |
| **文风字符串** | `pipeline` 与 **`/api/novel-style/{style}/...`** 中的 **`{style}`** 使用上文 **「与 `WritingPipeline.fromPath` 对齐的前端映射表」** 中的别名；**未收录的任意字符串会静默解析为 `POWER_FANTASY`**（HTTP 多为 200）。 |
| **续写 URL 与真实流水线** | **`POST /api/novel-style/{style}/{novelId}/continue`** 中路径里的 **`{style}` 不覆盖** 该书已保存的 **`writingPipeline`**；实际生成以 **`GET .../pipeline`** 为准。要换文风先 **`POST /api/novel/{novelId}/pipeline`**，再续写。 |
| **M9 展示与预期** | 若 UI 展示跨章状态：调 **`GET /api/novel/{novelId}/narrative-state`**，**`narrativeState` 可为 `null`**（开关关、尚无章节落库、或旧书无列数据）。**第 1 章初稿**服务端 **不会** 注入 M9 快照块（无「上一快照」）；**第 2 章起**由服务端在模型上下文中拼接，**前端无需也不应**自行拼该块。 |
| **删除书** | 先 **`GET /api/novel/{novelId}/delete-guard`**，再 **`POST /api/novel/{novelId}/delete`** 按返回规则传 **`confirmTitle` / `typedPhrase` / `acknowledgeIrreversible`**（见 §2.2）。 |
| **大纲与监控** | 创建页默认值：**`GET /api/novel/config/outline-plan-defaults`**；完整页面—接口映射与推荐调用流见 **§4「前端联调手册」**（尤其 **§4.5～§4.9**）。 |
| **同源联调页** | 见上文 **「同源静态页「导演台」`/novels.html`」**；部署后打开 **`http://<host>:8080/novels.html`**，与 web-agent **并行对照**同一套 **`/api/novel/...`**。 |

### 常见问题（前端常问）

| 问题 | 简答 |
|------|------|
| **传的 `pipeline` / 风格为什么变成爽文（`POWER_FANTASY`）？** | 仅 **`fromPath` 映射表**内别名会落到对应枚举；其余一律默认爽文。见 **「与 `WritingPipeline.fromPath` 对齐的前端映射表」**。 |
| **`novel-style/.../continue` 用了 `light-novel` 为什么正文还是爽文？** | 续写以书内已存 **`writingPipeline`** 为准；URL 里 `{style}` 主要影响响应里的 **`pipelineHint`**。见 **「`POST /api/novel-style/{style}/...` 里的 `{style}`」** 与 §3。 |
| **能否用 HTTP 单独改 `narrativeCarryover`（M4 承接）？** | **不能**；承接由服务端在章节成功落库后维护。可调 **`writingStyleParams.narrative`** 等影响后续生成。见文首 **「叙事相关（M1–M4）与前端」** 与 **[前端与用户指引.md](./前端与用户指引.md)**。 |
| **M9 开了，`narrative-state` 仍是 `null`？** | 可能尚未有任何章节 **成功落库**、开关实际为 `false`，或解析失败看 **`message`**。首章生成前无快照属正常。 |
| **为什么第一章没有 M9 块 / 用户看不到跨章衔接？** | **预期**：至少需 **上一章已成功写入** 后才会聚合出快照；**第一章初稿**无上一快照，故 **无 M9 注入**。见 §1.3。 |
| **`TASK_CONFLICT` 是什么？** | 章节类生成与区间锁、或与 **重新生成大纲** 等互斥；详见下文 **「`TASK_CONFLICT` 触发规则」**。 |
| **书存在为什么访客 `GET` 小说详情是 404？** | **`libraryPublic=false`** 时访客 **不可见**，与物理不存在 **同源 404** 策略。见 §0 与 §2。 |
| **`/novels.html` 和产品里的页面是什么关系？** | **`/novels.html`** 是 **本仓库自带的联调页**，见 **「同源静态页「导演台」`/novels.html`」**；产品界面在 **web-agent**，两边 **共用后端 API**，职责不同。 |
| **某角色在前文剧情里死了，后文还会被写出来吗？** | **会仍有较高概率被提及或「写活」**：服务端 **没有**「角色已死亡」专用字段或 REST；`character_profile` **不随剧情自动删除**；每章仍注入 **全书角色档案** 与 **姓名锁定**（`immutableConstraints`）。事实记忆里的 **`character_state`** 只是「正文含该姓名的段落摘录」，**不理解**生死语义。姓名一致性检测按「上一章是否出现该姓名」等规则工作，**不等于**「死后禁止出场」。合理做法：在 **`POST .../continue` 等 `generationSetting`** 或 **`POST .../writing-style-params`** 备注中写明禁忌；必要时 **重生后续章** 或人工改文。详见下节。 |

### 角色生死与后续出场（剧情语义｜当前实现）

本节说明 **剧情层面** 行为，**不是** HTTP 路径变更。

| 要点 | 说明 |
|------|------|
| **有无「宣告死亡」接口** | **无**。不提供单独 API 将角色标记为死亡或从生成上下文中剔除。 |
| **角色表是否保留** | **`GET .../characters`** 与库表 **`character_profile`** 长期保留该书角色；剧情写死某人 **不会** 自动删档。 |
| **每章生成注入什么** | 仍会带上 **完整角色设定块**、基于档案生成的 **姓名锁定**（见 **`EntityConsistencyService`**：`MUST_APPEAR_OR_EXPLAIN` / `NO_RENAME` / `ALLOW_ABSENT_NO_RENAME`）。模型始终能看到已故角色的设定摘要，除非你在本章 **`generationSetting`** 里明确约束。 |
| **章节事实记忆** | **`GET .../chapter-facts`**、`buildFactMemory` 中的 **`character_state`**：某章若正文出现该姓名，会存一条「含姓名的段落裁切」；**不做 NLP 意义上的「死了没」判断**。回忆、悼词、他人转述仍会出现该姓名，并进入后续记忆窗口。 |
| **一致性检测侧效应** | **`detectNameConsistencyIssue`**：若某锁定姓名在 **上一章正文** 中出现而 **本章** 解析到的姓名集合里没有，可能触发「缺失角色名」并走窄幅修订——逻辑是 **防漂移**，**不是**「死后必须缺席」。主角/女主规则更紧，配角相对松（依赖上一章是否出现）。若死亡后你希望多章完全不提姓名，一般不会由此强制「把人写回来」，但若模型为消除告警硬插一句名字，仍可能发生；以实际告警与修订提示为准。 |
| **大纲 / cast** | **`outlineGraphJson.cast`** 与 Markdown 大纲 **不会** 因某一章剧情自动改写；重做大纲才会整体更新。 |
| **实务建议** | 关键剧情点后用 **`generationSetting`** 写明「某某已死，后文禁止以其存活身份登场，仅允许回忆/他人提及」；严重跑偏时对后续章 **`POST .../chapters/{n}/regenerate`**；长期方案需 **产品级「角色状态机」**（当前仓库未实现）。 |

---

## 文风与流水线：HTTP URL 路径段与 `pipeline` 字符串（≠ 本机磁盘路径）

下文「路径」均指 **HTTP 里的 URL path 段**（以及 JSON body 里的 **`pipeline` 字符串**），**不是**服务器或开发机上的文件夹路径。

### 与 `WritingPipeline.fromPath` 对齐的前端映射表

服务端用 **`WritingPipeline.fromPath(字符串)`** 解析（大小写不敏感）。**未出现在下表「可接受别名」中的任意字符串**都会**静默**解析为默认爽文 **`POWER_FANTASY`**（HTTP 一般仍 **200**，不会因「不认识的风格」单独返回 404；404 多来自书库权限或资源不存在）。

| 内部枚举（落库 / `GET .../pipeline` 常见返回值） | 建议前端使用的 path / `pipeline` 别名（kebab-case 等） | 说明 |
|-----------------------------------------------|--------------------------------------------------------|------|
| `POWER_FANTASY` | `power-fantasy`（UI「默认爽文」推荐）、`power_fantasy`、`pf`、`default`、`shuang`、`shuangwen`、`爽文` | 默认爽文 / 通用网文分支 |
| `LIGHT_NOVEL` | `light-novel`（推荐）、`light_novel`、`lightnovel`、`light`、`ln` | 轻小说 |
| `SLICE_OF_LIFE` | `slice-of-life`（推荐）、`slice_of_life`、`sliceoflife`、`slice`、`daily` | 日常向 |
| `PERIOD_DRAMA` | `period-drama`（推荐）、`period_drama`、`perioddrama`、`period`、`age`、`era`、`年代`、`年代文` | 年代文 |
| `VULGAR` | `vulgar`（推荐）、`rough`、`俗`、`粗俗`、`粗口` | 粗俗风（口语/江湖气，合规底线仍由提示词约束） |

**风格约定**：`fromPath` **不是** `Enum.valueOf`。枚举名全大写经 `toLowerCase` 后，**`light_novel`、`power_fantasy`** 等也已落入对应分支；前端仍**建议**在 URL / `pipeline` 中统一用 **kebab-case**（如 `light-novel`、`power-fantasy`），与文档及路由可读性一致。无法归类的串一律按 **`POWER_FANTASY`** 处理。

### `POST /api/novel-style/{style}/...` 里的 `{style}`

- **URL 形态**：`POST {apiBase}/api/novel-style/{style}/create`、**`POST {apiBase}/api/novel-style/{style}/{novelId}/continue`**。其中 **`{style}` 为路径里紧跟 `/api/novel-style/` 的第一段**，取值用上表别名即可（例如轻小说：`.../api/novel-style/light-novel/...`）。
- **`.../create`**：`{style}` 会 **`fromPath` → 写入新书流水线**，响应里的 **`pipeline`** 为解析后的**枚举名字符串**（如 `LIGHT_NOVEL`）。
- **`.../{novelId}/continue`**：续写任务与 **`POST /api/novel/{novelId}/continue`** 相同队列逻辑；**实际生成使用的流水线 = 该书已保存的 `writingPipeline`**（见 `GET /api/novel/{novelId}` / `GET .../pipeline`）。本接口 URL 中的 **`{style}` 仅用于响应字段 `pipelineHint`**（同样为 `fromPath(style)` 的枚举名），**不会**在续写前自动改书的流水线。若要让某章按轻小说写，请先 **`POST /api/novel/{novelId}/pipeline`** 将 `pipeline` 设为 `light-novel` 等，再续写。

### `POST /api/novel/{novelId}/pipeline` 的 body 字段 `pipeline`

- Body 示例：`{"pipeline":"light-novel"}`。语义与上表 **`{style}`** 相同，经 **`fromPath`** 落库；**建议与 novel-style URL 使用同一套 dash 别名**，避免随意字符串被当成爽文。

---

## 写入接口总览（前端对接清单）

以下为当前后端暴露的 **POST 写操作**（小说域 + 登录）；成功响应多为 **Map**：`status`、`code`、`message`，并与表格「成功时额外扁平字段」合并（与文首「小说管理 Map 形态」一致）。  
**鉴权**：启用 `app.security.enabled` 且该书 **`libraryPublic=false`** 时，除 **`POST /api/auth/login`**、**`POST /api/novel/create`** 外，凡访问该书资源的接口须在请求头携带 **`Authorization: Bearer <jwt>`**（管理员）。**`POST /api/novel/{novelId}/library-visibility`** 即使书为公开也要求管理员身份。

| 路径 | 作用 | 请求体（JSON，字段均可按需省略 unless 说明必填） | 典型错误码 | 成功时关注字段 |
|------|------|---------------------------------------------------|------------|----------------|
| `POST /api/auth/login` | 管理员登录 | `username`、`password` | `AUTH_DISABLED`、`UNAUTHORIZED` | `token`、`role` 等（见 §0） |
| `POST /api/novel/create` | 新建书并入队初稿引导任务 | `topic`（必填）；`generationSetting`、`hotMemeEnabled`、`writingStyleParams`（对象：**§16.2.1～16.2.6** 全书 JSON）、`serializationPlatform`、`creatorNote`、`outlineDetailedPrefixChapters`、`outlineMinRoadmapChapters` | `INVALID_ARGUMENT`、`CREATE_TASK_FAILED`、`TASK_CONFLICT` | `novelId`、`taskId`、`taskType` |
| `POST /api/novel/{novelId}/continue` | 续写单章（异步任务） | `chapterNumber`（缺省=下一空章）、`generationSetting`（本章附加设定） | `CONTINUE_TASK_FAILED`、`TASK_CONFLICT` | `taskId`、`taskType`、`targetChapter` |
| `POST /api/novel/{novelId}/auto-continue` | 自动续写到目标章（异步） | `targetChapterCount`（全书目标章号；缺省仅补一章）、`generationSetting` | `AUTO_CONTINUE_TASK_FAILED`、`TASK_CONFLICT` | `taskId`、`taskType`、`rangeFrom`、`rangeTo` |
| `POST /api/novel/{novelId}/chapters/{chapterNumber}/regenerate` | 单章重生（异步） | `generationSetting` | `REGENERATE_TASK_FAILED`、`TASK_CONFLICT`、`INVALID_ARGUMENT` | `taskId`、`taskType`、`targetChapter` |
| `POST /api/novel/{novelId}/chapters/regenerate-range` | 区间重生（异步） | `startChapter`、`endChapter`（必填）、`generationSetting` | `REGENERATE_RANGE_TASK_FAILED`、`TASK_CONFLICT`、`INVALID_ARGUMENT` | `taskId`、`taskType`、`startChapter`、`endChapter` |
| `POST /api/novel/{novelId}/outline/regenerate` | 重新生成大纲（同步，耗时可很长） | `hint`、`outlineDetailedPrefixChapters`、`outlineMinRoadmapChapters` | `OUTLINE_REGENERATE_FAILED`、`TASK_CONFLICT`、`INVALID_ARGUMENT` | `novelId` |
| `POST /api/novel/{novelId}/writing-style-params` | 更新全书参数 JSON（仅影响后续生成） | `writingStyleParams`（对象）或 **`writingStyleParamsRaw`**（字符串）；根级可含 **§16.2.2～16.2.6** 所列分支（文风枚举、`narrative`、M6、认知弧线、文笔四层等）；**整树写回**；空体表示清空 | `INVALID_STYLE_PARAMS`、`STYLE_PARAMS_UPDATE_FAILED` | `novelId`、`writingStyleParams` |
| `POST /api/novel/{novelId}/book-meta` | 连载平台 / 创作说明（不参与 AI） | `serializationPlatform`、`creatorNote`（未出现则不修改；`""` 清空） | `INVALID_ARGUMENT`、`BOOK_META_UPDATE_FAILED` | `novelId`、`serializationPlatform`、`creatorNote` |
| `POST /api/novel/{novelId}/hot-meme` | 全书热梗开关 | `enabled`（布尔） | `HOT_MEME_UPDATE_FAILED` | `novelId`、`hotMemeEnabled` |
| `POST /api/novel/{novelId}/pipeline` | 切换文风流水线（后续章节生效） | `pipeline`（字符串，见下「流水线字段」） | `PIPELINE_UPDATE_FAILED` | `pipeline` |
| `POST /api/novel/{novelId}/library-visibility` | 书库是否对访客公开 | `libraryPublic`（必填布尔）；须 **管理员** | `INVALID_ARGUMENT`、**403** | `novelId`、`libraryPublic` |
| `POST /api/novel/{novelId}/characters/repair` | 异步修复/重建角色设定 | `forceRegenerate`、`rebuildMode`（如 `all`/`partial`）、`characterContextHint`、`targetCharacterNames`、`extraHint` | `CHARACTER_REPAIR_TASK_FAILED`、`INVALID_ARGUMENT` | `forceRegenerate`、`rebuildMode` |
| `POST /api/novel/{novelId}/delete` | 永久删除全书 | `confirmTitle`、`typedPhrase`、`acknowledgeIrreversible`（均必填，规则见 §2.2） | `DELETE_CONFIRM_MISMATCH` | `novelId` |
| `POST /api/novel/tasks/{taskId}/cancel` | 取消任务 | 无 body | `TASK_CANCEL_FAILED` | `taskId` |
| `POST /api/novel/tasks/{taskId}/retry` | 重试失败/已取消任务 | 无 body | `TASK_RETRY_FAILED` | `taskId`、`status` |
| `POST /api/novel/tasks/{taskId}/kick` | 手动触发 PENDING 执行 | 无 body | `TASK_KICK_FAILED` | `taskId` |
| `POST /api/novel-style/{style}/create` | 按 URL 文风创建书（异步 QQ 侧同款链路） | `topic`（必填）；其余同 `create` | `STYLE_CREATE_TASK_FAILED`、`INVALID_ARGUMENT` | `pipeline`、`hotMemeEnabled` |
| `POST /api/novel-style/{style}/{novelId}/continue` | 按文风入口续写（任务队列） | `chapterNumber`、`generationSetting` | `STYLE_CONTINUE_TASK_FAILED`、`TASK_CONFLICT` | `taskId`、`pipelineHint` |

**流水线字段 `pipeline`（`POST .../pipeline`）与 novel-style 的 `{style}`**：一律经 **`WritingPipeline.fromPath`** 解析；**完整别名表、URL 形态、`continue` 与 `pipelineHint` 语义**见上文 **「文风与流水线：HTTP URL 路径段与 `pipeline` 字符串」**。

**叙事相关（M1–M4）与前端**：  
- **读**：`GET /api/novel/{novelId}` 返回实体字段 **`narrativeCarryover`**（跨章承接摘要，可能为 `null`）、**`writingStyleParams`**（字符串 JSON，可含 **§16.2** 所列任意受支持分支）。  
- **写**：通过 **`POST .../writing-style-params`** 更新全书微参（含 **`narrative`**、可选 **`narrativePhysicsMode`**）；**无**单独「仅更新承接文案」的 HTTP 接口——承接由服务端在章节成功落库后维护（见 **`novel.narrative-engine.m4-carryover-enabled`**）。  
- **服务端叙事开关**：属 `application.yml`，非 REST 写入；文档见 **§16.2.7** 与 **架构1.0**。  
- **M7 只读侧车**：`GET /api/novel/{novelId}/narrative-artifacts`（有数据的章列表）、`GET /api/novel/{novelId}/chapters/{chapterNumber}/narrative-artifact`（单章；无数据时 `artifact` 为 `null`）；与 **`chapter-sidecar`**（事实/衔接锚点）互补，互不替代。
- **M9 跨章状态（可选）**：`GET /api/novel/{novelId}/narrative-state` 返回书本级 **`narrativeState`** JSON（由 **`novel.narrative-engine.m9-crosscut-enabled`** 控制是否在每章成功落库后写入 **`novel.narrative_state_json`**）；聚合 M4 承接预览、本章 sidecar、近期 `sidecar_fact`、最新 **`plot_snapshot`**，并含 **`relationshipHints`**（自 **`character_state`** 事实摘录）、**`tensionRippleHint`**（据「当前章 vs 最近阶段快照章」的规则提示）、**`schemaVersion`**（≥2 时出现深化字段），**不替代** `chapter_fact` / 快照表存储，仅只读汇总。**开启 M9 时**，生成 **第 2 章及以后** 初稿会在服务端上下文拼接 **上一章已成功落库后** 的快照摘录（`NovelAgentService` 内 `【M9 跨章叙事状态快照…】` 块，有上限字符），与 M4 承接、事实记忆等并列供模型衔接；**第 1 章初稿不注入**（无上一快照）。

**一般 Vue 前端不接**：`POST /api/qq/message`、`POST /api/qq/cleanup`（机器人/运维）；若要做一体化控制台再接入。

各接口细节、示例 JSON 与只读 GET 列表仍见下文 **§0～§29** 与 **§3 文风分流**。

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
- `INVALID_STYLE_PARAMS`: 文风微参 JSON 无效或不包含支持的字段
- `STYLE_PARAMS_UPDATE_FAILED`: 文风微参更新失败（非预期异常）
- `BOOK_META_UPDATE_FAILED`: 书籍备注（连载平台/创作说明）更新失败
- `HOT_MEME_UPDATE_FAILED`: 热梗开关更新失败
- `OUTLINE_REGENERATE_FAILED`: 重新生成大纲失败（非预期异常）
- `TASK_CONFLICT`: 章节类生成与区间锁冲突，或与「重新生成大纲」互斥（详见下文「`TASK_CONFLICT` 触发规则」）
- `TASK_NOT_FOUND`: 任务不存在
- `TASK_CANCEL_FAILED`: 任务取消失败（任务不存在或已完成）
- `TASK_RETRY_FAILED`: 任务重试失败（仅 FAILED/CANCELLED 可重试）
- `TASK_KICK_FAILED`: 手动触发执行失败（仅 PENDING 可 kick）
- `DELETE_CONFIRM_MISMATCH`: 永久删除小说时，书名确认、固定短语或「知晓不可恢复」勾选未通过服务端校验
- `AUTH_DISABLED`: 调用了登录接口但当前未启用 `app.security.enabled`（见 `/api/auth/login`）
- `UNAUTHORIZED`: 登录用户名或密码错误（HTTP 401，Map 形态）

**书库权限相关（启用 `app.security` 时）**：
- 访客请求非公开书：`GET /api/novel/{novelId}` 等多数按小说维度的接口返回 **404** + `ApiResponse`（code 404），**不是** 403（避免暴露「书存在但无权」）。
- 非管理员调用 **`POST /api/novel/{novelId}/library-visibility`**：返回 **403** + `ApiResponse`，`message` 为「需要管理员登录」。

前端建议：
- `status=success && code=OK` 视为成功
- 失败时**只向用户展示 `message`**（Toast/弹窗）；`code` 用于分支或埋点，勿把堆栈、原始英文异常贴给用户
- 若 `code=TASK_CONFLICT`，提示用户稍后重试，并刷新 `GET /api/novel/{novelId}/generation-tasks`（必要时再看 `regeneration-tasks` / `writing-monitor`）

`TASK_CONFLICT` 触发规则（重要）：
- 后端已启用**数据库级任务锁**（基于 `generation_task`），跨实例生效（本地/云端共享同库也生效）。
- 只要目标小说存在 `PENDING` 或 `RUNNING` 且与本次请求章节区间重叠的任务，就会拒绝新任务。
- 因此同一本小说同一区间不会被两个实例并发写入，避免章节互相覆盖。
- **大纲与章节互斥**：重新生成大纲期间会在库中写入一条 `taskType=OUTLINE_REGENERATE`、`RUNNING` 的占位任务（区间字段为 `0-0`，不参与章节区间重叠判断）；此时任何章节类异步入队（续写、自动续写、区间重生、初始开书任务等）以及对应的重试会返回 `TASK_CONFLICT`。反之，只要本书存在处于 `PENDING`/`RUNNING` 的章节类任务（`INITIAL_BOOTSTRAP`、`CONTINUE_SINGLE`、`AUTO_CONTINUE_RANGE`、`REGENERATE_RANGE`），**不允许**再调用重新生成大纲（同样返回 `TASK_CONFLICT`）。单机进程内另有章节区间与大纲的**内存守护**，与 DB 租约配合，避免「大纲重写」与「QQ 直连续写/区间重生」在同实例上并发踩章节。
- **大纲占位任务的恢复**：应用重启后，若仍存在未正常结束的 `OUTLINE_REGENERATE` 记录，启动恢复逻辑会将其收尾为结束态，避免永久阻塞章节任务（该记录**不由**通用 worker 执行正文生成）。

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

## 0) 书库访问控制与管理员登录 `/api/auth`

### 配置开关（后端）

- `app.security.enabled`：`false`（默认）时不校验 JWT，书库**全部小说可见**，接口行为与旧部署一致；`true` 时启用下列规则。
- `app.security.admin-username` / `app.security.admin-password`：管理员账号密码（密码建议用环境变量 `ADMIN_PASSWORD` 注入）。
- `app.security.jwt-secret` / `app.security.jwt-expiration-ms`：签发 JWT 用（密钥建议环境变量 `JWT_SECRET`）。

### 小说字段 `libraryPublic`（书库是否对访客公开）

- 实体/JSON 字段名：**`libraryPublic`**（布尔）。
- `true`（默认）：未登录访客可在 `GET /api/novel/list` 中看到该书，并可访问该书相关 HTTP 详情/章节/任务等接口（在启用安全的前提下仍须满足「公共」或携带管理员 token）。
- `false`：**仅**携带有效管理员 JWT 的调用方可列表可见、可读详情与章节等；访客调用单书接口按 **404** 处理。

### 1. 管理员登录

- 方法: `POST`
- 路径: `/api/auth/login`
- 前置: `app.security.enabled=true`；若为 `false`，返回 **400**，`code=AUTH_DISABLED`，`message` 说明未启用登录。
- Body(JSON):
```json
{
  "username": "admin",
  "password": "<密码>"
}
```
- 成功（HTTP 200，Map 形态）示例字段：
  - `status`: `success`，`code`: `OK`，`message`: `登录成功`
  - `token`: JWT 字符串（前端存本地后每次请求加 Header）
  - `tokenType`: `Bearer`
  - `expiresInMs`: 过期毫秒数
  - `role`: `ADMIN`
- 失败（HTTP 401）：`code=UNAUTHORIZED`，`message` 为简短中文（如用户名或密码错误）。

### 2. 当前会话（是否管理员）

- 方法: `GET`
- 路径: `/api/auth/me`
- 请求头: 可选 `Authorization: Bearer <token>`
- 返回（JSON 对象，非文首 Map 壳）示例：
  - `securityEnabled` (Boolean)：是否与配置一致启用安全
  - `authenticated` (Boolean)：当前请求是否识别为管理员（有效 Bearer）
  - `role` (String | null)：管理员为 `ADMIN`，否则 `null`
- 说明：`securityEnabled=false` 时 `authenticated` 一般为 `false`，书库不做隐藏；前端可按 `securityEnabled` 决定是否展示登录入口。

### 启用安全后的调用约定（前端联调）

- 需要管理员权限的书库读写在请求中加：**`Authorization: Bearer <token>`**。
- **`GET /api/novel/list`**：未登录仅返回 `libraryPublic=true` 的小说；管理员返回全部。
- **所有以 `novelId` 为路径参数的 `/api/novel/...` 读接口**（含章节、大纲、进度、导出、`delete-guard`、任务列表等）：若该书 `libraryPublic=false` 且请求非管理员 → **404**。
- **`POST /api/novel/{novelId}/library-visibility`**：仅管理员；否则 **403**。
- **`GET|POST /api/novel/tasks/{taskId}`**（及 cancel/kick/retry）：服务端根据任务关联的 `novelId` 做同样的书库可读校验。
- **`POST /api/novel/create`**：不要求管理员 JWT（新建书默认 `libraryPublic=true`，可由管理员后续改为仅管理员可见）。
- **QQ / 机器人直连业务服务**的路径（非上述 HTTP 书库清单）：不受 `libraryPublic` 约束；若也要限制需另行产品设计。

静态页 **`http://<host>:8080/novels.html`**（path **`/novels.html`**）已支持：可选登录、列表与书库标签、**新建**、**删除守卫**、**`frontend-runtime`**、按书 **导演台**（监控 / M9 / 流水线 / 任务、**cancel/kick/retry**、**pipeline / hot-meme / writing-style-params / book-meta**、**continue / auto-continue**）。定位见 **「同源静态页「导演台」`/novels.html`」**。

---

## 1) 小说管理接口 `/api/novel`

### 1. 获取小说列表
- 方法: `GET`
- 路径: `/api/novel/list`
- 作用: 获取小说基础信息列表（实体数组；字段含 **`libraryPublic`** 等）
- 请求参数: 无
- 请求头: 可选 `Authorization: Bearer <token>`（启用 `app.security` 时，管理员可看全部；否则仅 **`libraryPublic=true`** 的书）
- 说明: `app.security.enabled=false` 时仍为全部小说（与旧版一致）
- 前端调用示例:
```json
{}
```

### 1.1 获取大纲规划默认值（创建页）

- 方法: `GET`
- 路径: `/api/novel/config/outline-plan-defaults`
- 作用: 返回服务端配置的默认「开篇细纲章数」「路线图覆盖末章」及允许范围，供前端表单初始化；可与 `POST /api/novel/create` 的可选字段对齐。
- 说明: 该路径由独立控制器注册（`NovelOutlineConfigController`），避免与 `/{novelId}` 等映射冲突；完整 URL 仍为 `/api/novel/config/outline-plan-defaults`。
- 返回: **Map 形态**（成功时扁平字段），典型字段：
  - `detailedPrefixChapters` / `minRoadmapChapters`：服务端当前默认（来自 `novel.outline.*`）
  - `detailedPrefixChaptersMin` / `detailedPrefixChaptersMax`：细纲章数建议区间（后端生成时仍会 clamp）
  - `minRoadmapChaptersMin` / `minRoadmapChaptersMax`：路线图末章建议区间

### 1.2 获取前端联调用运行时配置

- 方法: `GET`
- 路径: `/api/novel/config/frontend-runtime`
- 作用: 返回与 UI 校验相关的**服务端当前配置**（**不含**密钥与密码）；便于与前端 `CONFIG.*` 常量对齐。字段与 `application.yml` 中 `app.security.*`、`novel.auto-continue.*`、`novel.outline.*`（含 **`outlineTwoPhaseGraphEnabled`**、**`outlineGraphPhaseMaxTokens`**）、`novel.narrative-engine.enabled` / `m7-artifact-enabled` / **`m9-crosscut-enabled`** 一致（部署若覆盖 yml，以此接口为准）。
- 鉴权: **无需** JWT。
- 返回: **Map 形态**，除 `status`/`code`/`message` 外扁平字段见文末 **「附录 A」**（含 **`m9CrosscutEnabled`**）。

### 1.3 获取跨章叙事状态快照（M9）

- 方法: `GET`
- 路径: `/api/novel/{novelId}/narrative-state`
- 作用: 返回本书 **`narrative_state_json`** 解析后的 **`narrativeState`** 对象（聚合 M4 承接预览、衔接锚点、侧车实体/事实摘要、近期 `sidecar_fact`、阶段快照摘录，以及 **`relationshipHints`**、**`tensionRippleHint`**、**`schemaVersion`** 等深化字段，见文首「叙事相关」）。需 **`novel.narrative-engine.m9-crosscut-enabled: true`** 且在章节成功生成后才会写入；关闭开关或旧书无数据时 **`narrativeState` 为 `null`**，并带简短 **`message`**。另：开关为 `true` 时，**从第 2 章起的初稿**会在服务端上下文附带 **上一章落库后** 的快照摘录块（**第 1 章初稿无上一快照，故不注入**；见 **「前端职责与常见问题」**）。
- 鉴权: 与其它 `novelId` 读接口相同（`guardRead`）。
- 返回: `novelId`、`narrativeState`（JSON 对象或 `null`）；解析失败时可能含 **`narrativeStateRaw`**。

### 2. 获取小说基础信息
- 方法: `GET`
- 路径: `/api/novel/{novelId}`
- 作用: 查询单本小说详情（标题/题材/设定/时间、**连载平台 `serializationPlatform`、创作说明 `creatorNote`**、`writingStyleParams` JSON 字符串、`hotMemeEnabled`、**书库可见 `libraryPublic`**、**叙事承接 `narrativeCarryover`**（M4：上一章落库后写入、供下一章开篇参考；未生成或关闭功能时可为 `null`）、创建时可选保存的 **`outlineDetailedPrefixChapters` / `outlineMinRoadmapChapters`**（用于首次大纲参数追溯）等，随实体字段返回）
- 路径参数:
  - `novelId` (Long, 必填)
- 请求头: 可选 `Authorization: Bearer <token>`（启用安全且本书 `libraryPublic=false` 时，**必须**为管理员 token 方可读取）
- 说明: 物理不存在或与访客权限下的「不可见」均返回 **404**，`ApiResponse` 体中 `message` 为简短中文（非「小说不存在，ID: xx」类内部句式；访客命中非公开书亦为同一类 404）

### 2.1 删除前：拉取确认所需信息（只读，无副作用）

- 方法: `GET`
- 路径: `/api/novel/{novelId}/delete-guard`
- 作用: 供前端展示删除确认表单：**当前书名**、须用户原样抄写的 **`requiredPhrase`**、进行中任务数量 **`activeTaskCount`**（`PENDING`/`RUNNING` 计数）、提示 **`hint`**
- 路径参数:
  - `novelId` (Long, 必填)
- 返回: **Map 形态**（与文首「小说管理 Map 形态」一致），成功时除 `status`/`code`/`message` 外扁平包含：
  - `novelId` (Long)
  - `title` (String)：当前库中书名字符串（删除比对时前后空格会被 trim，须与此 trim 结果一致）
  - `requiredPhrase` (String)：固定为 **`我确认永久删除此书`**（后端常量 `NovelDeletionService.REQUIRED_PHRASE`，勿在前端写死另一套文案）
  - `activeTaskCount` (Number)：本书进行中生成任务数量（删除接口会先尝试取消这些任务再清理数据）
  - `hint` (String)：给人读的注意事项摘要
- 前端调用示例:
```json
{}
```
- 返回示例（字段名与真实接口一致）:
```json
{
  "status": "success",
  "code": "OK",
  "message": "请展示确认表单后再提交删除",
  "novelId": 12,
  "title": "某某修仙传",
  "requiredPhrase": "我确认永久删除此书",
  "activeTaskCount": 1,
  "hint": "须完整输入当前书名、原样抄写 requiredPhrase，并勾选知晓不可恢复；提交后会取消进行中任务并永久清除本书全部章节与关联数据。"
}
```

### 2.2 永久删除小说（多步确认，不可恢复）

- 方法: `POST`
- 路径: `/api/novel/{novelId}/delete`
- 作用: **永久删除**该小说及其关联数据：先对本书 `PENDING`/`RUNNING` 任务执行取消，再删除章节事实、一致性告警、剧情快照、生成日志、生成任务、角色档案、章节，最后删除小说本体。**不可撤销**。
- 路径参数:
  - `novelId` (Long, 必填)
- Body(JSON)，**三项缺一不可**：
```json
{
  "confirmTitle": "与 GET delete-guard 返回的 title 在 trim 后完全一致",
  "typedPhrase": "我确认永久删除此书",
  "acknowledgeIrreversible": true
}
```
- 字段说明:
  - `confirmTitle` (String, 必填)：用户输入的书名，须与当前库中书名的 **trim 后** 完全一致（含全角/半角、标点差异都会失败）
  - `typedPhrase` (String, 必填)：须与 `GET .../delete-guard` 返回的 **`requiredPhrase` 完全一致**（前后空格会被 trim；内容固定为「我确认永久删除此书」）
  - `acknowledgeIrreversible` (Boolean, 必填)：须为 **`true`**，表示用户勾选「知晓删除不可恢复」
- 成功: `status=success`，`code=OK`，`message` 如「小说及其关联数据已永久删除」，并含 `novelId`
- 失败: `status=error`，`code=DELETE_CONFIRM_MISMATCH`，`message` 为简短中文（如书名不一致、确认句错误、未勾选知晓等）
- 说明:
  - 本接口**不使用 GET**，避免误触与爬虫预取；前端须在用户明确提交后再 `POST`
  - 内置参考页（与后端同源部署时）: **`http://<host>:8080/novels.html`** — 列表 + 删除确认 + 导演台等，见 **「同源静态页「导演台」`/novels.html`」**

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
  - `outline`：AI 大纲正文（Markdown，与实体 `description` 同源）
  - **`outlineGraphJson`**：两阶段大纲成功时由服务端写入的**冲突图谱整段 JSON 字符串**（`novel.outline.two-phase-graph-enabled: true` 且第一阶段校验通过）；单阶段回退或未开两阶段时为 **`null`**。前端可 **`JSON.parse`** 后使用根字段 `setup`、`conflicts`、`tension_matrix`、`cast` 等（**`cast` 为已定稿核心角色表**，见下节）。
  - `ready` (Boolean)
  - `globalSetting`
  - `serializationPlatform`：连载平台（展示用）
  - `creatorNote`：创作说明/本书用途（展示用，与 AI 大纲 `outline` 不同）

#### 4.0 大纲冲突图谱 JSON（`outlineGraphJson`）与 **`cast`**

- **存储**：字段 **`outline_graph_json`**（接口中为 **`outlineGraphJson`**），与 Markdown 大纲 **`description` / `outline`** 一并更新（创建书、`bootstrap`、`POST .../outline/regenerate` 等路径）。
- **读取**：**`GET /api/novel/{novelId}`**（实体）、**`GET /api/novel/{novelId}/outline`**（Map）均返回 **`outlineGraphJson`**。
- **何时有数据**：`novel.outline.two-phase-graph-enabled` 为 **`true`** 且第一阶段模型输出通过服务端校验（含 **`cast`** 等）；否则图谱阶段失败并**回退单阶段**，此时 **`outlineGraphJson` 常为 `null`**（仅 Markdown 大纲仍有）。
- **`cast`（结构化角色）**：图谱根下的数组，每项至少含 **`name`**（全书固定称谓）、**`role`**（如 `protagonist`/主角、`antagonist`/对立）、**`want`**、**`fear`**、**`knowledge`**（可为空字符串）、**`summary`**。服务端用其与 Markdown 大纲、**角色档案生成**对齐姓名口径；**不等价于** `GET .../characters` 返回的已落库档案（档案仍为独立生成/入库）。
- **兼容**：模型若使用根字段 **`characters`**，服务端会规范化合并为 **`cast`** 后再持久化。

### 4.1 重新生成大纲（可写建议）

- 方法: `POST`
- 路径: `/api/novel/{novelId}/outline/regenerate`
- 作用: 调用模型**重新生成**全书故事大纲，并**覆盖**写入本书的 AI 大纲字段（与 `GET .../outline` 返回的 `outline` / 实体 `description` 同源）。**同步接口**：请求会一直占用到模型返回为止，前端请设置足够长的 HTTP 超时（或后续若改为异步任务再对接任务 ID）。
- 路径参数: `novelId` (Long, 必填)
- 请求头: 与本书其它接口一致（启用书库安全且为非公开书时需管理员 JWT）。
- Body(JSON)，**全部可选**；空体 `{}` 表示仅按当前题材、设定、流水线与本书已存的大纲规划参数重生成，不加额外说明：
```json
{
  "hint": "中期反派不要过早露面；前五章只做线索铺垫；希望主线偏向智斗而非纯战力碾压。",
  "outlineDetailedPrefixChapters": 45,
  "outlineMinRoadmapChapters": 150
}
```
- 字段说明:
  - `hint` (String, 可选)：作者/编辑对**本次大纲**的补充建议（节奏、禁忌、必须保留或必须延后的剧情点、情感基调等）。服务端当前上限约 **12000 字符**，超出返回 `INVALID_ARGUMENT`。
  - `outlineDetailedPrefixChapters` / `outlineMinRoadmapChapters` (Integer, 可选)：若传入，会先**更新**小说实体上对应字段再生成（规则与创建小说时相同，后端仍会 clamp）。
- 成功: Map 形态，`status=success`，`code=OK`，`message` 提示已写入；含 `novelId`。
- 失败:
  - `INVALID_ARGUMENT`：`hint` 过长或非法参数；小说不存在等同逻辑错误亦可能映射为此类。
  - `TASK_CONFLICT`：本书已有进行中的章节类生成任务（见上文 `TASK_CONFLICT` 规则），或本进程内章节写入区间仍被占用；`message` 会说明「须等待章节任务结束」或「章节写入进行中」等。
  - `OUTLINE_REGENERATE_FAILED`：模型或服务端非预期异常。
- **同步过程中的任务列表**：大纲重写进行中时，`GET .../generation-tasks` 可能出现一条短暂的 `OUTLINE_REGENERATE`、`RUNNING` 记录（占位租约），前端可将此类任务理解为「大纲重写占用中」，勿与章节正文 worker 混淆。
- **重要副作用提示**:
  - **已写成正文的章节不会自动改写**，新旧大纲可能不一致；若需对齐，请自行对相应章节使用「重生」等能力。
  - 生成日志中会记录一次 `generation_type=outline` 的成功条目（备注区分是否带 `hint`）。

### 5. 获取当前流水线
- 方法: `GET`
- 路径: `/api/novel/{novelId}/pipeline`
- 作用: 获取该小说当前使用的文风流水线、文风微参 JSON、书籍备注，以及 **热梗开关**、**书库是否公共**（便于详情页 / 导演台一次拉齐，避免额外 `GET .../{id}`）
- 返回示例:
```json
{
  "novelId": 1,
  "pipeline": "LIGHT_NOVEL",
  "writingStyleParams": "{\"dialogueRatioHint\":\"high\",\"humorLevel\":\"low\"}",
  "serializationPlatform": "晋江",
  "creatorNote": "练笔，慢热日常向",
  "hotMemeEnabled": false,
  "libraryPublic": true
}
```

### 5.1 更新书库可见性（公共 / 仅管理员）

- 方法: `POST`
- 路径: `/api/novel/{novelId}/library-visibility`
- 作用: 将本书标记为 HTTP 书库对访客是否可见（**不**影响 QQ 机器人等业务直连逻辑）
- 路径参数: `novelId` (Long, 必填)
- 请求头: 启用 `app.security` 时须 **`Authorization: Bearer <管理员 token>`**；未启用安全时任意调用者可改（与「全员等同管理员」的旧行为一致）
- Body(JSON):
```json
{
  "libraryPublic": false
}
```
- 字段: `libraryPublic` (Boolean, **必填**)；`true` 公共，`false` 仅管理员可在书库列表/详情中看到并调用该书相关 HTTP 接口
- 成功: Map 形态，`status=success`，并返回 `novelId`、`libraryPublic`
- 失败: 未登录或非管理员且安全已启用 → **403** + `ApiResponse`（`message`：需要管理员登录）；参数缺失 → `INVALID_ARGUMENT`

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
  - `period` / `period-drama` / `age` / `era` / `年代` / `年代文`
  - `vulgar` / `rough` / `俗` / `粗俗` / `粗口`

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
- 返回: `GenerationLog` 实体列表；章节成功时 **`contextNote`** 可能含叙事引擎摘要（如 `emotion=… band=…`），便于对照 `writingStyleParams.narrative` 与管线默认是否生效

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

### 12.1 列出叙事引擎侧车（M7）

- 方法: `GET`
- 路径: `/api/novel/{novelId}/narrative-artifacts`
- 作用: 返回本书各章已落库的 **叙事引擎中间产物 JSON**（Planner、Lint、可选 **M8 批评** `critic` 块等）；**仅包含有数据的章**（从未开启 M7、或开关关闭、或旧章无生成记录时可能为空数组 `[]`）。
- 返回: `List<Map>`，每项含 `chapterNumber`、`chapterTitle`、`artifact`（已解析的 JSON 对象）；若库内字符串非法 JSON 则该项可能为 `artifactRaw`（字符串）。

### 12.2 获取单章叙事引擎侧车（M7）

- 方法: `GET`
- 路径: `/api/novel/{novelId}/chapters/{chapterNumber}/narrative-artifact`
- 作用: 单章侧车；章节不存在时 **404**；存在但尚无快照时 **`artifact` 为 `null`**，并带 `message` 说明。
- 返回字段: `novelId`、`chapterNumber`、`chapterTitle`、`artifact`（JSON 对象或 `null`）；解析失败时可能含 `artifactRaw`。

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
  - `writePhase` / `isBusyPersisted` / `writeCursorChapter` / `writeRangeFrom` / `writeRangeTo`
    - 用于展示“小说是否正在续写/重写”的持久化工作台状态

### 15. 获取创作全过程
- 方法: `GET`
- 路径: `/api/novel/{novelId}/creative-process`
- 作用: 返回小说+角色+章节+日志+时间线的聚合视图
- 补充: 时间线首节点 `novel_created` 中含 `serializationPlatform`、`creatorNote`，便于「开书信息」展示

### 16. 新建小说
- 方法: `POST`
- 路径: `/api/novel/create`
- 作用: 发起“题材 -> 大纲 -> 角色 -> 初始章节”异步生成
- Body(JSON):
```json
{
  "topic": "转生成为修仙界魔剑",
  "generationSetting": "可选，用户补充设定",
  "hotMemeEnabled": false,
  "writingStyleParams": {
    "styleIntensity": "balanced",
    "dialogueRatioHint": "medium",
    "humorLevel": "low",
    "periodStrictness": "normal"
  },
  "serializationPlatform": "起点",
  "creatorNote": "上架连载，都市修仙向",
  "outlineDetailedPrefixChapters": 40,
  "outlineMinRoadmapChapters": 120
}
```
- 字段说明:
  - `topic` (String, 必填)
  - `generationSetting` (String, 可选)
  - `hotMemeEnabled` (Boolean, 可选，默认 `false`)：全书级「少量网络热梗」；仅影响**后续**生成，宜克制开启
  - `writingStyleParams` (Object, 可选)：全书参数 JSON 根对象（文风枚举、**`narrative` 情绪参数**、**`narrativePhysicsMode`**、**认知弧线**、**文笔四层** 等可并存），字段类型与范围见 **§16.2.1～16.2.6**；仅影响**后续**生成
  - `serializationPlatform` (String, 可选)：连载平台，**仅展示**，不参与 AI
  - `creatorNote` (String, 可选)：创作说明（本书用途、受众、备注），**仅展示**，不参与 AI（勿与大纲正文混淆）
  - `outlineDetailedPrefixChapters` (Integer, 可选)：首次生成大纲时「开篇逐章细纲」最少章数；不传则用服务端 `novel.outline.detailed-prefix-chapters`；后端 clamp 约 **15～150**
  - `outlineMinRoadmapChapters` (Integer, 可选)：首次大纲全书路线图覆盖的末章号下限；不传则用 `novel.outline.min-roadmap-chapters`；后端 clamp 约 **≥细纲+15**、上限 **600**
- 返回新增字段:
  - `novelId`: 新建小说 ID（立即落库）
  - `taskId`: 可恢复任务 ID
  - `taskType`: `INITIAL_BOOTSTRAP`
  
> 说明：该任务可在进程重启后继续执行（任务入库 `generation_task`）。

#### 16.1 调整全书热梗开关（不影响已生成章节）

- 方法: `POST`
- 路径: `/api/novel/{novelId}/hot-meme`
- Body(JSON): `{ "enabled": true }`
- 作用: 更新小说的 `hotMemeEnabled`；之后续写/重生等生成路径会按新开关拼接提示约束（已落库正文不会自动改写）。

#### 16.2 更新文风微参（仅影响后续生成）

- 方法: `POST`
- 路径: `/api/novel/{novelId}/writing-style-params`
- Body(JSON) 二选一:
```json
{
  "writingStyleParams": {
    "periodStrictness": "strict",
    "dialogueRatioHint": "medium"
  }
}
```
或
```json
{
  "writingStyleParamsRaw": "{\"humorLevel\":\"low\"}"
}
```
- 说明:
  - 空请求体或 `writingStyleParams` 传 `{}`：表示**清空**已存参数。
  - JSON **语法非法**，或根对象**不包含任何受支持字段**（见 **§16.2.1** 表中「受支持分支」）：返回 **`INVALID_STYLE_PARAMS`**。
  - **存库**：服务端对合法 JSON **`readTree` 校验通过后整树写回**（保留 `narrative`、认知弧线、文笔四层等与早期「仅四类文风枚举」无关的字段）。前端应 **`JSON.parse`** 详情/`pipeline` 返回的字符串 → 编辑**同一根对象** → **`JSON.stringify`** 提交；多 Tab 表单需在客户端**合并**后再 POST，避免只提交局部键导致覆盖丢失。
- 返回: `writingStyleParams` 当前库中字符串（可能为 `null`）

#### 16.2.1 全书 `writingStyleParams`：受支持分支一览（前端配置入口）

根类型：**JSON Object**（接口里常以 **字符串** 存储/传输）。下列 **任一分支**有有效内容即可通过 **`POST .../writing-style-params`** 校验（可与其它分支**叠加**）。

| 分支 | 键（根级） | 类型概要 | 用途 |
|------|------------|----------|------|
| 文风枚举 | `styleIntensity`、`dialogueRatioHint`、`humorLevel`、`periodStrictness`（及 snake_case 别名） | string 枚举 | 对白占比、幽默、年代考究、叙事张扬度等「标签式」微调 |
| 叙事引擎·情绪 | **`narrative`**（对象） | 见 **§16.2.3** | 情绪类型、强度带宽 **\[0,1\]**、压抑度、禁止短语、节奏/材质/视角hint、轻小说三维等 |
| 叙事物理 M6 | **`narrativePhysicsMode`** | string | 连续微扰 vs 压力阈值分桶，见 **§16.2.4** |
| 认知弧线 | **`narrativeArcPhase`**、**`cognitionArc`** | string + object | 全书阶段与人物判断/代价取向，见 **§16.2.5** |
| 文笔四层 | **`rhythm`**、**`perception`**、**`language`**、**`informationFlow`**（或 `information_flow`） | object | 句法节奏、感知权重、词粒度、信息揭示策略，见 **§16.2.6** |

**前端建议**：详情页 / 导演台用 **Tab 或折叠面板** 对应上表五类，共用内存中的 **一个** `writingStyleParams` 对象；保存前深合并（若分模块编辑）或始终读写完整对象。**各分支同时存在时是否「打架」、运维关叙事引擎后哪些仍生效**：见 **§16.2.8**。

---

#### 16.2.2 文风枚举参数（根级）

与叙事参数**并列**于同一 JSON 根下；字段均可缺省。

| 字段 | 类型 | 取值（大小写不敏感） | 含义 |
|------|------|----------------------|------|
| `styleIntensity` | string | `mild` \| `balanced` \| `bold` | 叙事外放程度：克制 / 平衡 / 更狠 |
| `dialogueRatioHint` | string | `low` \| `medium` \| `high` | 对白占比倾向 |
| `humorLevel` | string | `low` \| `medium` \| `high` | 幽默与打趣程度 |
| `periodStrictness` | string | `loose` \| `normal` \| `strict` | 年代细节严苛度（年代文最有用） |

**别名（根级 snake_case）**：`style_intensity`、`dialogue_ratio_hint`、`humor_level`、`period_strictness`。

**生效**：大纲/角色/初稿「用户文风微调」块；**主润色**阶段再次拼接同类说明。

---

#### 16.2.3 叙事引擎·情绪与带宽（根对象下的 **`narrative`** 对象）

**类型**：JSON Object。与 **`writingPipeline` 默认 Narrative Profile** 合并：**仅覆盖出现的键**；**`forbidden`**（字符串数组）与管线默认禁止项 **叠加去重**。

| 字段 | 类型 | 范围 / 约束 | 说明 |
|------|------|-------------|------|
| `emotionType` | string | 自由文本 | 情绪类型标签（如「克制对峙」） |
| `intensityMin` | number | **\[0, 1\]** | 情绪强度带宽下限（服务端 clamp） |
| `intensityMax` | number | **\[0, 1\]** | 情绪强度带宽上限；若小于 min 会交换 |
| `suppression` | number | **\[0, 1\]** | 压抑度 |
| `triggerFact` | string | 自由文本 | 触发锚点（事实短描述） |
| `forbidden` | string[] | 每项非空字符串 | 禁止出现的短语（子串级 Lint 与提示共用） |
| `rhythmHint` | string | 自由文本 | 本章节奏意图 |
| `textureHint` | string | 自由文本 | 语言材质意图 |
| `povHint` | string | 自由文本 | 视角/镜头意图 |
| `affection` | number | **\[0, 1\]**（可选） | 轻小说向：亲昵维 |
| `awkwardness` | number | **\[0, 1\]**（可选） | 轻小说向：尴尬维 |
| `assertiveness` | number | **\[0, 1\]**（可选） | 轻小说向：强硬维 |
| `interactionFocus` | string | 自由文本 | 互动写法焦点 |
| `readerInferenceRule` | boolean | `true` / `false` | 是否强调推断、减少标签情绪句 |

**命名**：除上表 camelCase 外，下列 **snake_case** 等价：`emotion_type`、`intensity_min`、`intensity_max`、`trigger_fact`、`rhythm_hint`、`texture_hint`、`pov_hint`、`interaction_focus`、`reader_inference_rule`。数值字段也可由 **数字字符串** 解析（与 JSON number 等价）。

**生效**：需 **`novel.narrative-engine.enabled=true`** 且有合并后的 Profile；注入 **初稿 / 润色 / 终稿去 AI 味** 及 M5/M6 等相关块（详见 **§16.2.7**）。

**示例**（根级可与文风枚举、M6、文笔等并列）：

```json
{
  "dialogueRatioHint": "medium",
  "narrativePhysicsMode": "stress",
  "narrative": {
    "emotionType": "克制对峙",
    "intensityMin": 0.45,
    "intensityMax": 0.88,
    "suppression": 0.4,
    "triggerFact": "证据摊开但仍欠一环",
    "forbidden": ["他很愤怒", "一股前所未有的"],
    "rhythmHint": "前紧后松，结尾留一击未落槌",
    "textureHint": "冷硬、短句偏多",
    "povHint": "贴近主角感官",
    "affection": 0.65,
    "awkwardness": 0.5,
    "assertiveness": 0.35,
    "interactionFocus": "每段动作+对白+微反应；一处话只说一半",
    "readerInferenceRule": true
  }
}
```

---

#### 16.2.4 叙事物理 M6（根级 **`narrativePhysicsMode`**）

| 字段 | 类型 | 取值 | 说明 |
|------|------|------|------|
| `narrativePhysicsMode` | string | 见下文别名（大小写不敏感） | 覆盖「叙事物理」分桶；缺省时 **轻小说/日常 → 连续微扰**，**其余管线 → 压力阈值** |

**连续微扰** 别名：`continuous`、`micro`、`daily`、`日常`、`连续`。  
**压力阈值** 别名：`stress`、`threshold`、`burst`、`压力`、`阈值`。

**生效**：初稿 M6 块；润色/终稿单行提醒（依赖叙事引擎开启）。

---

#### 16.2.5 认知弧线（根级 **`narrativeArcPhase`** + **`cognitionArc`**）

用于全书阶段下的人物判断、犹豫类型与错误代价尺度（提示词约束，非 Lint 字段）。

| 字段 | 类型 | 范围 / 枚举 | 说明 |
|------|------|-------------|------|
| `narrativeArcPhase` | string | `early` / `mid` / `late`（及 `前期`/`中期`/`后期`等别名） | 选用哪一阶段默认表或 `byPhase` 桶 |
| `cognitionArc` | object | 可选内嵌 **`byPhase`**：`{ "early": {...}, "mid": {...}, "late": {...} }` | 覆盖默认；扁平字段与 `byPhase` 当前阶段切片合并 |

**`cognitionArc` 内常用字段**（均可选；数值 **\[0, 1\]**）：`cognitiveBiasLevel`、`hesitationType`（string）、`decisionLatencyHint`、`errorConsequenceHint`、`arcBeatHint`。支持 snake_case。

**启用规则**：仅有合法 **`narrativeArcPhase`** 而无 `cognitionArc` 时使用**内置阶段默认**；两者皆无则**不注入**。仅有 **`cognitionArc` 而无阶段** 时默认阶段为 **`early`**（仅合并 `byPhase.early` —— 写中后期请**务必**设 `narrativeArcPhase`）。

**别名**：`narrative_arc_phase`、`cognition_arc`、`by_phase`。

**生效**：初稿认知块；**M2 Planner** 附加「认知弧线对齐」说明（Planner 开启且运行时）。

---

#### 16.2.6 文笔四层旋钮（根级四个对象）

控制「信息组织 + 感知选择 + 句法节奏 + 词粒度」（与 **`narrative` 情绪带宽** 正交）。四个对象均可缺省；**某一对象内至少有一个合法数值或 `revealType` 文本** 即启用对应层。

**通用**：下列 **0～1** 旋钮均为 **number**，服务端 **clamp 到 \[0,1\]**；键名支持 **snake_case**（表中仅列 camelCase）。

**① `rhythm`（句子节奏）**

| 字段 | 类型 | 范围 | 含义（语义） |
|------|------|------|----------------|
| `sentenceLengthVariance` | number | \[0, 1\] | 长短句落差 / 「呼吸感」 |
| `pauseDensity` | number | \[0, 1\] | 停顿、留白密度 |
| `fragmentation` | number | \[0, 1\] | 碎片句、断裂感 |

**② `perception`（感知与镜头权重）**

| 字段 | 类型 | 范围 | 含义 |
|------|------|------|------|
| `sensoryWeight` | number | \[0, 1\] | 感官画面比重 |
| `conceptualWeight` | number | \[0, 1\] | 概念/规则解释比重 |
| `externalActionWeight` | number | \[0, 1\] | 外部行动推进比重 |
| `internalThoughtWeight` | number | \[0, 1\] | 内心活动比重 |

**③ `language`（语言颗粒度）**

| 字段 | 类型 | 范围 | 含义 |
|------|------|------|------|
| `abstractionLevel` | number | \[0, 1\] | 抽象 vs 具象 |
| `wordPrecision` | number | \[0, 1\] | 择词精度 |
| `adjectiveControl` | number | \[0, 1\] | 形容词节制（高 = 更节制） |
| `adjectiveDensity` | number | \[0, 1\] | 与 `adjectiveControl` **二选一**（别名含义）；二者均出现时优先读 `adjectiveControl` |
| `technicalDensity` | number | \[0, 1\] | 术语/知识密度 |

**④ `informationFlow`（信息揭示）**（对象键亦可为 **`information_flow`**）

| 字段 | 类型 | 范围 / 枚举 | 含义 |
|------|------|-------------|------|
| `revealType` | string | 建议 `immediate`、`layered`、`withheld`（及中文如 `即时`/`分层` 等扩展） | 直接揭示 / 分层揭示 / 长期隐匿 |
| `uncertaintyMaintenance` | number | \[0, 1\] | 维持推断空间、避免作者代读者宣判 |
| `clarityDelay` | number | \[0, 1\] | 澄清延后：场面先行 vs 早解释 |

**生效**：初稿「文笔旋钮」提示块；**主润色**（步骤 4 `PolishingAgent.polish`）再次注入**同款说明 + 「润色阶段对齐」**（避免为顺滑抹平 deliberate 取向）。Lint/Flow/M8 等窄幅 pass **不重复**注入全文块。

**示例**：

```json
{
  "rhythm": { "sentenceLengthVariance": 0.6, "fragmentation": 0.4 },
  "perception": { "sensoryWeight": 0.8, "internalThoughtWeight": 0.7, "externalActionWeight": 0.3 },
  "language": { "abstractionLevel": 0.6, "technicalDensity": 0.7, "adjectiveControl": 0.3 },
  "informationFlow": { "revealType": "layered", "uncertaintyMaintenance": 0.7 }
}
```

---

#### 16.2.7 服务端叙事开关与里程碑（`application.yml`，非 REST）

以下为运维/后端配置，前端只需知晓「关闭则对应能力不注入」。

- **合并规则（`narrative`）**：缺省字段用当前 **`writingPipeline` 管线默认值** 补齐；`forbidden` 与管线默认**叠加**（去重）。
- **生效范围（叙事引擎）**：章节初稿、润色、终稿去 AI 味均参考合并后的叙事配置（见 **`novel.narrative-engine.enabled`**）。
- **M2 章前 Planner**：`novel.narrative-engine.two-phase-enabled: true` 时，初稿前多一次模型调用生成「叙事脚本」；失败静默回退。轻小说且 **`novel.light-novel.chapter-planning-enabled`** 时默认 **跳过** 叙事 Planner（`novel.narrative-engine.two-phase-skip-with-light-novel-plan`）。
- **可调服务端键（节选）**：`novel.narrative-engine.enabled`、`two-phase-enabled`、`two-phase-skip-with-light-novel-plan`、`planner-temperature`、`planner-max-tokens`；**M3**：`lint-enabled`、`lint-fix-enabled`、`lint-fix-temperature`、`lint-fix-max-tokens`；**M4**：`m4-carryover-enabled`；**M7**：`m7-artifact-enabled`（且需 `enabled`）；**M8**：`m8-critic-enabled`、`m8-rewrite-enabled`、`m8-critic-temperature`、`m8-critic-max-tokens`、`m8-rewrite-temperature`、`m8-rewrite-max-tokens`、`m8-rewrite-min-severity`；批评 JSON 可选 **`tooSmooth`** / **`too_smooth`**；**M9**：`m9-crosscut-enabled`。**阻力层 / 流体**：`resistance-layer-enabled`、`flow-pass-enabled`、`flow-pass-temperature`、`flow-pass-max-tokens`。
- **M5**：情绪波形与管线句法力学由服务端提示词注入（无单独 REST 字段）；依赖叙事引擎总开关。

#### 16.2.8 参数并存、边界与前端须知（「冲突」与优先级）

本节说明：**JSON 内多类参数能否同时保存**（能）、**后端是否校验语义矛盾**（否）、以及**与运维开关、管线提示叠加时前端应如何理解**，避免产品与联调误解。

##### （1）不存在「字段级互斥」校验

- 根对象可同时包含：**文风枚举**、**`narrative`**、**`narrativePhysicsMode`**、**认知弧线**、**文笔四层** 等；服务端 **不会** 因「例如 `rhythm` 与 `rhythmHint` 语义不一致」而返回错误。
- **`INVALID_STYLE_PARAMS`** 仅表示：JSON **语法非法**，或根对象 **不包含 §16.2.1 所列任一分支的有效内容**（见 §16.2、附录 E）。**不表示**用户配置的旋钮组合是否「推荐」。

##### （2）服务端强制 **二选一**（与本书 JSON 无关）

| 能力 A | 能力 B | 行为 |
|--------|--------|------|
| **M2 叙事 Planner**（`novel.narrative-engine.two-phase-enabled`） | **轻小说章前节拍规划**（`novel.light-novel.chapter-planning-enabled`，且本书 **`writingPipeline` 为轻小说**） | 默认 **不跑 M2**，避免两套章前节拍重复、双倍调用；由 **`novel.narrative-engine.two-phase-skip-with-light-novel-plan`** 控制（默认一般为 `true`）。 |

**前端须知**：导演台若看到「叙事 Planner 跳过」，可能是上述互斥而非用户 JSON 错误；可在 **`GET .../config/frontend-runtime`** 或运维文档确认 yml。

##### （3）叙事引擎总闸关闭后：**`narrative` 不注入，认知弧线与文笔仍注入**

当运维将 **`novel.narrative-engine.enabled`** 设为 **`false`** 时（非本书 JSON；前端用 **`GET /api/novel/config/frontend-runtime`** 返回的 **`narrativeEngineEnabled`** 与 UI 对齐）：

| 本书 `writingStyleParams` 内容 | 章节生成侧典型行为 |
|--------------------------------|-------------------|
| **`narrative` 对象** | **不参与**合并 Profile；初稿 **无** M1/M5/M6 叙事引擎块，**无** M2 Planner，**无** 依赖 Profile 的 Lint/Flow/M8 叙事链路等 |
| **`narrativeArcPhase` / `cognitionArc`** | **仍会**解析并注入初稿（及 M2 若单独不可能运行则无 Planner 对齐段） |
| **`rhythm` / `perception` / `language` / `informationFlow`** | **仍会**注入初稿与 **主润色**（`PolishingAgent.polish`） |

**前端须知**：勿向用户传达「关掉叙事引擎 = 本书所有 `writingStyleParams` 高级选项全部失效」——**认知弧线**与**文笔四层**仍可能生效；若产品希望「引擎关则表单一并灰显」，请根据 **`narrativeEngineEnabled`**（见 **`GET .../frontend-runtime`**）联动禁用 **`narrative` / M6 / 认知弧线 / 文笔** 等 Tab（是否一并禁用认知与文笔由产品设计决定，本节仅陈述后端实际注入行为）。

##### （4）语义重叠（软拉扯）：未报错，但模型需折中

以下属 **同一美学维度多段提示** 叠加，可能使模型在「节奏 / 情绪 / 推进感」之间取舍；建议前端表单 **同一维度尽量单一数据源**（例如节奏以 **`rhythm` 数值**为准，`rhythmHint` 仅作补充说明且勿矛盾）。

| 重叠维度 | 可能叠加的来源 |
|----------|----------------|
| **句法节奏** | `narrative.rhythmHint`（自由文案）与 **`rhythm`** 对象（`sentenceLengthVariance` 等） |
| **情绪 vs 判断** | **`narrative`**（强度带宽、压抑度等）与 **认知弧线**（阶段偏置、犹豫类型、代价尺度） |
| **推进感** | **`writingPipeline` 固定块**（如爽文「一章一事起落」）与 **`perception.externalActionWeight` 过低**；文笔块内已有平衡提示，仍属软约束 |
| **信息节奏** | **`informationFlow.revealType`** 与管线/大纲对「一章揭露量」的习惯 |

##### （5）提示优先级（便于前端写 Tooltip）

合并进模型上下文的多块约束中，文案层面的一般原则是：**大纲事实与合规 > 本书 immutable / 审核结果 > 管线硬约束 > 文风枚举与叙事/文笔等微调**。文笔块内亦写明次于大纲与合规。**没有任何 REST 字段可让前端「覆盖」大纲正文事实**。

##### （6）主润色 vs 后续窄幅 Pass

- **文笔四层**：注入 **初稿** + **主润色**（全文润色一步）。
- **M3 Lint 修订 / Flow 润滑 / M8 批评重写** 等 **窄幅** pass **通常不再附带**整段文笔旋钮全文；理论上存在「后续 Pass 略抹平刻意断裂/留白」的轻微风险，一般弱于主润色约束。

---

#### 16.3 更新书籍备注（连载平台 / 创作说明）

- 方法: `POST`
- 路径: `/api/novel/{novelId}/book-meta`
- Body(JSON): 字段均可按需出现；**未出现的字段不修改**；传空字符串 `""` 可清空该字段
```json
{
  "serializationPlatform": "番茄小说",
  "creatorNote": "备份稿，未首发"
}
```
- 作用: 仅更新展示字段，**不参与**大纲/章节 AI 生成
- 失败: `BOOK_META_UPDATE_FAILED` + 简短 `message`

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
- 返回新增字段:
  - `taskId`: 持久化任务 ID（可用于重启后继续追踪）
  - `taskType`: `CONTINUE_SINGLE`
- 并发规则:
  - 若该章已被其他 `PENDING/RUNNING` 任务占用，会返回 `TASK_CONFLICT`

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
- 返回新增字段:
  - `taskId`
  - `taskType`: `AUTO_CONTINUE_RANGE`
  - `rangeFrom` / `rangeTo`
- 并发规则:
  - 若目标区间与任一活跃任务区间重叠，会返回 `TASK_CONFLICT`

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
- 返回新增字段:
  - `taskId`
  - `taskType`: `REGENERATE_RANGE`
  - `targetChapter`
- 并发规则:
  - 若目标章节已被活跃任务占用，会返回 `TASK_CONFLICT`

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
- 返回新增字段:
  - `taskId`
  - `taskType`: `REGENERATE_RANGE`
- 并发规则:
  - 若目标区间与活跃任务区间重叠，会返回 `TASK_CONFLICT`

### 23. 查询区间重生任务状态
- 方法: `GET`
- 路径: `/api/novel/{novelId}/regeneration-tasks`
- 作用: 查询当前是否有重生任务在运行，以及运行中的区间
- 返回示例:
```json
{
  "novelId": 1,
  "hasRunningTask": true,
  "runningRanges": ["12-20"],
  "persistedWorkbench": {
    "writePhase": "REGENERATING_RANGE",
    "writeRangeFrom": 12,
    "writeRangeTo": 20,
    "writeCursorChapter": 14,
    "isBusyPersisted": true
  }
}
```

### 24. 获取写作监控聚合（推荐前端主看）
- 方法: `GET`
- 路径: `/api/novel/{novelId}/writing-monitor`
- 作用: 同时返回
  - DB 持久化工作台（小说级状态）
  - 内存任务锁区间（volatile guard）
  - 非 `READY` 章节标记清单
- 返回关键字段:
  - `writePhase` / `writeCursorChapter` / `isBusyPersisted`
  - `volatileGuardRanges`
  - `chaptersWithActiveWriteMarks`

### 25. 获取该小说的持久化任务列表
- 方法: `GET`
- 路径: `/api/novel/{novelId}/generation-tasks`
- 作用: 查询本小说所有可恢复任务（按创建时间倒序）
- **`taskType` 取值（章节 worker 可执行）**：`INITIAL_BOOTSTRAP`（开书批量）、`CONTINUE_SINGLE`、`AUTO_CONTINUE_RANGE`、`REGENERATE_RANGE`。
- **占位类型（非章节正文 worker）**：`OUTLINE_REGENERATE` — 表示「重新生成大纲」占用中的 DB 租约；请求结束后应变为 `DONE`（或异常进程重启后由恢复逻辑收尾）。**勿**对该类型调用 kick 期望生成章节；若列表中长期滞留异常状态，可结合取消接口或运维清理 DB。

### 26. 查询单个任务详情
- 方法: `GET`
- 路径: `/api/novel/tasks/{taskId}`
- 作用: 查询指定任务状态、区间、当前进度、错误信息等

### 27. 取消任务
- 方法: `POST`
- 路径: `/api/novel/tasks/{taskId}/cancel`
- 作用: 将任务标记为 `CANCELLED`，执行器每章前会检查并停止后续执行

### 28. 重试任务
- 方法: `POST`
- 路径: `/api/novel/tasks/{taskId}/retry`
- 作用: 将 `FAILED` / `CANCELLED` 任务重新置为 `PENDING` 并自动执行
- 限制: `DONE` 任务不可重试
- 额外限制:
  - 若重试任务区间与其他活跃任务重叠，会返回 `TASK_CONFLICT`

### 29. 手动触发 PENDING 任务执行
- 方法: `POST`
- 路径: `/api/novel/tasks/{taskId}/kick`
- 作用: 运维兜底按钮：当任务处于 `PENDING` 但因进程重启/事务时序等原因未自动启动时，可手动 kick 一次
- 失败返回:
  - `TASK_KICK_FAILED`

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
- **`{style}`**：HTTP 路径段，**不是**磁盘路径；取值与 **`WritingPipeline.fromPath`** 一致，**完整别名表**见文首 **「文风与流水线：HTTP URL 路径段与 `pipeline` 字符串」**。
- 摘要：`light-novel` / `ln` → 轻小说；`slice-of-life` / `daily` → 日常；`period-drama` / `年代文` → 年代；`vulgar` / `粗俗` → 粗俗风；**`power-fantasy` / `pf` / `爽文` 等 → 默认爽文**；其余任意字符串 → 默认爽文
- Body(JSON):
```json
{
  "topic": "校园社团日常",
  "generationSetting": "偏轻松、对白多",
  "hotMemeEnabled": false,
  "writingStyleParams": {
    "dialogueRatioHint": "high",
    "humorLevel": "medium"
  },
  "serializationPlatform": "未发布",
  "creatorNote": "QQ 机器人侧练笔"
}
```
- 可选字段：与 **小说管理 `/api/novel` →「### 16. 新建小说」** 所列可选体字段一致：`hotMemeEnabled`、`writingStyleParams`、`serializationPlatform`、`creatorNote`、`outlineDetailedPrefixChapters`、`outlineMinRoadmapChapters`（后两项影响首次大纲提示规模）。

### 2. 按文风入口续写
- 方法: `POST`
- 路径: `/api/novel-style/{style}/{novelId}/continue`
- **`{style}`**：仅参与 **`fromPath` → 响应字段 `pipelineHint`**（枚举名，如 `LIGHT_NOVEL`）；**不会**修改数据库中的 **`writingPipeline`**。**实际续写分支**与 **`POST /api/novel/{novelId}/continue`** 一样，由 **`GET .../pipeline`** 或详情里的 **`writingPipeline`** 决定。若 UI 用「点轻小说入口续写」，应确保此前已 **`POST .../pipeline`** 写入 `light-novel`（或创建书时即用 novel-style create 选好风格）。
- 请求头: 与小说管理一致；启用 `app.security` 且该书 **`libraryPublic=false`** 时须 **`Authorization: Bearer <管理员 token>`**，否则按书库规则返回 **404**（与 `GET /api/novel/{novelId}` 一致）
- Body(JSON):
```json
{
  "chapterNumber": 12,
  "generationSetting": "本章希望偏日常互动"
}
```

> 补充：成功响应含 **`taskId`**，可接入通用任务轮询；另含 **`pipelineHint`** 供 UI 对照 URL 中的 `{style}`，**勿与书本真实流水线混用**。

---

## 4) 前端联调手册（完整）

本节汇总 **Vue / 任意前端** 与后端联调所需的约定：鉴权、字段、轮询、叙事参数与写入清单交叉引用。详细 HTTP 路径与 Body 仍以下文 **§1～§3** 及本文 **「写入接口总览（前端对接清单）」** 一节为准。

### 4.1 环境与请求约定

- **Base URL**：`http(s)://<host>:8080`（默认端口 `8080`，与 `server.port` 一致）。
- **Content-Type**：POST Body 均为 **`application/json`**（除非接口另有说明）。
- **跨域**：控制器已 `CrossOrigin`，浏览器直连需注意_cookie 与本域策略（若前后端分离部署）。
- **鉴权头**：启用 `app.security.enabled` 时，管理员 JWT 使用 **`Authorization: Bearer <token>`**（由 `POST /api/auth/login` 返回）；详见 **§0**。
- **书库 404 语义**：匿名访问 **`libraryPublic=false`** 的书时，多数按小说维度的接口故意返回 **404**（防枚举）；前端应提示「需要登录」或「无权限」，勿提示「小说不存在」误导用户。

### 4.2 核心数据字段速查

| 场景 | 接口 | 前端用法 |
|------|------|----------|
| 小说列表 | `GET /api/novel/list` | 渲染书架；未登录时仅 **`libraryPublic=true`**（启用安全时）。 |
| 小说详情 | `GET /api/novel/{novelId}` | 实体字段：`title`、`topic`、`generationSetting`、`description`（大纲正文 Markdown）、**`outlineGraphJson`**（两阶段大纲时的冲突图谱 JSON **字符串**，单阶段或未生成可为 `null`）、`writingPipeline`、`writingStyleParams`（**字符串 JSON**）、`hotMemeEnabled`、`libraryPublic`、`serializationPlatform`、`creatorNote`、`outlineDetailedPrefixChapters`、`outlineMinRoadmapChapters`、**`narrativeCarryover`**（M4，只读，可为 `null`）、时间戳与工作台字段等。 |
| 大纲单独拉取 | `GET /api/novel/{novelId}/outline` | Map：`outline`（Markdown）、**`outlineGraphJson`**（与详情同源，可为 `null`）、`globalSetting`、`ready`、`serializationPlatform`、`creatorNote` 等；适合大纲 Tab；**结构化角色表**见下文 **「大纲冲突图谱与 cast」**。 |
| 流水线 + 微参快照 | `GET /api/novel/{novelId}/pipeline` | `pipeline`（枚举名字符串）、`writingStyleParams` 当前字符串；编辑表单初始化。 |
| 章节列表 | `GET /api/novel/{novelId}/chapters` | `Chapter[]`：`chapterNumber`、`title`、`content`、`writeState`（生成中占位）等。 |
| 叙事引擎侧车列表（M7） | `GET /api/novel/{novelId}/narrative-artifacts` | 仅含已落库快照的章；每项 `chapterNumber`、`chapterTitle`、`artifact`（JSON）。 |
| 单章叙事引擎侧车（M7） | `GET /api/novel/{novelId}/chapters/{n}/narrative-artifact` | `artifact` 可为 `null`；无该章 **404**。 |
| 跨章叙事状态（M9） | `GET /api/novel/{novelId}/narrative-state` | `narrativeState` 书本级聚合 JSON；依赖 `m9-crosscut-enabled`。 |
| 任务列表 | `GET /api/novel/{novelId}/generation-tasks` | `tasks` 数组：`status`（`PENDING`/`RUNNING`/`DONE`/`FAILED`/`CANCELLED`）、`taskType`、`rangeFrom`/`rangeTo`、`currentChapter`、`heartbeatAt` 等。 |
| 单任务 | `GET /api/novel/tasks/{taskId}` | 轮询主接口之一；先 `guardTask` 书库可读。 |
| 写作监控（推荐） | `GET /api/novel/{novelId}/writing-monitor` | 聚合 DB 任务 + 内存区间锁 + 非 READY 章节；适合详情页「生成中」横幅与禁用按钮逻辑。 |
| 进度摘要 | `GET /api/novel/{novelId}/progress` | `chapterCount`、`outlineReady`、`charactersReady`、`failedCount`、工作台字段。 |
| 创建页默认 | `GET /api/novel/config/outline-plan-defaults` | 表单默认与 clamp 区间。 |
| 运行时配置快照 | `GET /api/novel/config/frontend-runtime` | `securityEnabled`、`jwtExpirationMs`、自动续写上下限、大纲 clamp、**`narrativeEngineEnabled`**、M7/M9、两阶段大纲等（与 yml 一致）；**§16.2.8** 依赖 **`narrativeEngineEnabled`** 解释叙事参数是否注入。 |

### 4.3 异步生成：轮询策略建议

- **触发**：`POST /create`、`POST .../continue`、`POST .../auto-continue`、`POST .../regenerate`、`POST .../regenerate-range`、`POST /api/novel-style/...` 等均返回 **`taskId`**（或须从 `generation-tasks` 取最新一条）。
- **轮询间隔**：任务为 `PENDING`/`RUNNING` 时建议 **2～5s** 拉一次 `GET /api/novel/tasks/{taskId}`，并行 **`GET .../writing-monitor`**（或 `generation-tasks`）更新 UI。
- **结束态**：`DONE` / `FAILED` / `CANCELLED` 停止轮询；成功后刷新 **`GET .../chapters`**、详情、`progress`。
- **大纲重写**：`POST .../outline/regenerate` 为 **同步长请求**，前端 HTTP 客户端超时建议 **≥ 5～15 分钟**（仍可能因网关断开失败，需产品提示）。
- **并发**：遇 **`TASK_CONFLICT`**（文首与 §60 附近规则）：刷新 `generation-tasks`，禁用冲突按钮并提示用户等待或取消任务（`POST .../tasks/{taskId}/cancel`）。

### 4.4 文风与叙事参数（前端表单）

- **全书流水线**：`POST /api/novel/{novelId}/pipeline`，Body `{"pipeline":"<字符串>"}`。字符串语义为 **HTTP/API 路径级别名**，与 **`/api/novel-style/{style}/...`** 的 `{style}` 共用 **`fromPath`** 规则；**完整列表**见文首 **「文风与流水线」** 一节（推荐 `power-fantasy`、`light-novel` 等 kebab-case）。
- **全书参数 JSON**：`POST .../writing-style-params`，根对象可同时包含：**文风枚举**（§16.2.2）、**`narrative` 情绪参数**（§16.2.3）、**`narrativePhysicsMode`**（§16.2.4）、**认知弧线**（§16.2.5）、**文笔四层**（§16.2.6）；或使用 **`writingStyleParamsRaw`** 传整段 JSON 字符串。校验与存库规则见 **§16.2** / **§16.2.1**。**并存与运维边界（叙事引擎关闭后谁仍生效等）**：**§16.2.8**。
- **只读展示**：详情 / pipeline 接口返回的 **`writingStyleParams`** 为字符串，前端需 **`JSON.parse`** 后再渲染表单；**`narrativeCarryover`** 仅展示或折叠调试，**无 PATCH 接口**，由服务端章节成功后维护。
- **叙事引擎服务端开关**：`novel.narrative-engine.enabled` 等为运维配置，前端只需知悉「关闭则后端不注入叙事块」；详见 **§16.2.7**。

### 4.5 页面模块与接口映射（建议）

| 页面模块 | 主要接口 |
|----------|----------|
| 登录 / 会话 | `GET /api/auth/me`、`POST /api/auth/login` |
| 书架 | `GET /api/novel/list` |
| 详情页头 / 元数据 | `GET /api/novel/{id}`、`GET .../pipeline`、`GET .../progress` |
| 大纲 Tab | `GET .../outline`、`POST .../outline/regenerate` |
| 章节阅读 / 列表 | `GET .../chapters` |
| 续写 / 自动续写 | `POST .../continue`、`POST .../auto-continue` |
| 单章 / 区间重生 | `POST .../chapters/{n}/regenerate`、`POST .../chapters/regenerate-range` |
| 任务中心 | `GET .../generation-tasks`、`GET .../tasks/{taskId}`、`POST .../cancel|retry|kick` |
| 监控横幅 | `GET .../writing-monitor` |
| 质量 / 事实 | `GET .../consistency-alerts`、`GET .../chapter-facts`、`GET .../chapter-sidecar`、**`GET .../narrative-artifacts`**（M7）、**`GET .../chapters/{n}/narrative-artifact`**（M7）、**`GET .../narrative-state`**（M9）、`GET .../plot-snapshots`、`GET .../export-health` |
| 导出 | `GET .../export` 或 QQ 附件路径（见 §2） |
| 管理员：书库可见性 | `POST .../library-visibility`（须管理员 JWT） |
| 永久删除 | `GET .../delete-guard`、`POST .../delete` |

### 4.6 当前能力与限制（避免误期望）

- **角色生死**：服务端 **不维护**「存活/死亡」状态机；已故角色 **仍保留在档案与锁定列表中**，后续章 **仍可能被模型写出**（含错误「复活」）。约束依赖 **`generationSetting`**、重生章节或人工修订；详见上文 **「角色生死与后续出场」**。
- **章节正文流式 SSE/WebSocket**：当前 HTTP **无** SSE；生成走 **异步任务 + 轮询**。若需流式需另行产品设计。
- **前端内置参考**：同源 **`/novels.html`** 见 **「同源静态页「导演台」`/novels.html`」**；含列表、登录、创建、删除守卫、**`frontend-runtime`**、导演台 **GET 聚合**、**POST pipeline / hot-meme / writing-style-params / book-meta / continue / auto-continue**、任务 **cancel/kick/retry**，可作联调对照（非唯一前端实现）。
- **QQ 模块**：`/api/qq/*` 多为机器人侧；一体化 Vue 控制台可不接。

### 4.7 错误与提示（重申）

- Map 失败体：**`status=error`**、`**code**`（机器可读）、`**message**`（给用户看的短中文）。
- 详见文首 **统一错误码** 与 **§4.8 推荐调用流** 中的冲突处理。

### 4.8 前端对接建议（关键）

- **分工与 FAQ**：完整清单与一问一答见上文 **「前端职责与常见问题（联调）」**（含 **`frontend-runtime`**、M9 首章预期、**`pipeline`** 与 novel-style 续写差异）。
- **书库安全开关**：启动时或进入应用先调 `GET /api/auth/me`；若 `securityEnabled=true`，列表/详情请求统一带上本地缓存的 `Authorization: Bearer`；未登录用户访问非公开书可能收到 **404**，勿当成「接口坏了」，应引导登录或提示无权限。
- **错误展示**: 失败响应优先展示 `message`；勿展示服务端堆栈或原始英文异常（详见文首「通用返回风格」与 [前端与用户指引.md](./前端与用户指引.md)）。
- **详情页书籍注释区**: 展示 `serializationPlatform`、`creatorNote`；数据源可选 `GET /api/novel/{id}`、`GET .../outline`、`GET .../pipeline`；勿把 `description`（AI 大纲）误标为用户备注。
- **解析 `writingStyleParams`**：字符串可能为 `null`；解析失败时按「无微参」展示并提示用户重存。
- `auto-continue` 输入建议:
  - 前端限制 `targetChapterCount > 当前章节数`
  - 前端限制 `targetChapterCount <= max-target`（当前默认 200，见 **§5**）
- 所有异步触发接口:
  - 点击后立即提示“任务已启动”
  - 轮询 **`tasks/{taskId}`** 与 **`writing-monitor`**（优于单独 `progress` 看占用）
- 质量监控页面可增加:
  - `consistency-alerts` 列表（看是否发生角色名漂移）
  - `chapter-facts` 列表（看连续性记忆是否正常）
  - `export-health` 一键体检结果
  - `plot-snapshots` 查看每 5 章主线快照

### 4.9 推荐调用流（Steps；请直接按此实现）

0. **（可选）登录与书库权限**
- 若产品启用 `app.security`：先 `GET /api/auth/me`；未登录则展示登录页并 `POST /api/auth/login` 取得 `token`；后续所有 `/api/novel`、文风续写等请求按需附带 Bearer。管理员需在详情或列表提供「公共 / 仅管理员」切换时调用 `POST /api/novel/{novelId}/library-visibility`。

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

3. **重新生成大纲（同步）**
- 调用：`POST /api/novel/{novelId}/outline/regenerate`
- 必须为请求配置**较长 HTTP 超时**（模型耗时可能达数分钟）。
- 提交前建议刷新 `GET /api/novel/{novelId}/generation-tasks`：若已有 `PENDING`/`RUNNING` 的章节类任务，应先等待或取消后再改大纲，否则会收到 `TASK_CONFLICT`。
- 若返回 `TASK_CONFLICT`：提示用户「章节生成进行中」或「大纲正在重写」，并刷新 `generation-tasks`（可见 `OUTLINE_REGENERATE` 或章节类任务）。
- 成功后刷新：`GET /api/novel/{novelId}/outline`（或详情接口中的大纲字段）。

4. **单章续写/自动续写**
- 单章续写：`POST /api/novel/{novelId}/continue`
- 自动续写：`POST /api/novel/{novelId}/auto-continue`
- 提交后：
  - toast “任务已启动（taskId=xxx）”
  - 优先轮询 `GET /api/novel/tasks/{taskId}`
  - 并行轮询 `GET /api/novel/{novelId}/writing-monitor` 展示章节占位与区间
- 冲突处理：
  - 若返回 `TASK_CONFLICT`，先刷新 `GET /api/novel/{novelId}/generation-tasks`
  - 提示“已有任务占用目标章节/区间，请等待当前任务结束或取消后重试”；若列表中有 `OUTLINE_REGENERATE` 且 `RUNNING`，表示「重新生成大纲」占用中，章节任务须待其结束。

5. **区间重生（关键）**
- 提交前必须先调：`GET /api/novel/{novelId}/regeneration-tasks`
- 若 `hasRunningTask=true`：
  - 阻止提交，提示“当前重生区间: ...”
- 提交：`POST /api/novel/{novelId}/chapters/regenerate-range`
- 提交后：
  - 轮询 `GET /api/novel/tasks/{taskId}`，直到 `status in [DONE,FAILED,CANCELLED]`
  - 并行轮询 `regeneration-tasks` / `writing-monitor`
- 若返回 `TASK_CONFLICT`：
  - 显示冲突提示并展示正在占用的任务（取 `generation-tasks` 列表中 `PENDING/RUNNING`）
  - 提供“取消任务”（`POST /api/novel/tasks/{taskId}/cancel`）入口（仅在你有权限时开放）
  - 再刷新：
    - `GET /api/novel/{novelId}/chapters`
    - `GET /api/novel/{novelId}/chapter-facts`
    - `GET /api/novel/{novelId}/plot-snapshots`
    - `GET /api/novel/{novelId}/progress`

6. **导出前检查**
- 调：`GET /api/novel/{novelId}/export-health`
- 若 `healthy=false`：
  - 展示 `issues` 并阻止直接导出
- 若 `healthy=true`：
  - 允许导出 `/api/qq/export/{novelId}`

7. **sidecar 可视化（建议）**
- 调：`GET /api/novel/{novelId}/chapter-sidecar`
- 用法：
  - 在章节详情右侧显示“本章关键事实 + 衔接锚点”
  - 与正文联动，帮助人工快速判断是否跑偏

8. **永久删除小说（防误触）**
- 入口按钮文案建议明显区分危险操作（如「删除本书…」二次进入确认页）
- 打开确认页或弹窗时先调：`GET /api/novel/{novelId}/delete-guard`
  - 展示 `title`、`requiredPhrase`、`activeTaskCount`、`hint`
  - `requiredPhrase` **以接口返回值为准**渲染给用户抄写（避免后端今后调整短语时前端硬编码过期）
- 表单校验（前端可做体验优化，**最终以服务端为准**）：
  - 输入框：`confirmTitle` 建议默认填空，由用户粘贴或手打，避免与列表展示不一致
  - 输入框：`typedPhrase` 须与 `requiredPhrase` 一致
  - 勾选：`acknowledgeIrreversible === true`
- 用户确认后：`POST /api/novel/{novelId}/delete`，Body 见 **「### 2.2 永久删除小说」**
- 成功：Toast `message`，跳转小说列表并移除该项（或 `router.replace` 回列表）
- 失败：`code=DELETE_CONFIRM_MISMATCH` 时只展示 `message`，引导用户核对书名与确认句

---

## 5) 配置项（与前端强相关）

`application.yml`:

- `novel.auto-continue.default-target`: 自动续写默认目标章节数（未传目标时）
- `novel.auto-continue.max-target`: 自动续写安全上限（防止误触发过大量生成）
- `novel.outline.detailed-prefix-chapters` / `novel.outline.min-roadmap-chapters`: 大纲生成默认参数（前端不传创建体字段时使用）；详见「### 1.1」「### 16」
- `novel.outline.two-phase-graph-enabled`: 是否启用「冲突图谱 JSON → Markdown」两阶段大纲；为 **`true`** 且第一阶段成功时 **`outlineGraphJson`** 含完整图谱（含 **`cast`**）。**`novel.outline.graph-phase-max-tokens`**：图谱阶段模型输出上限（角色表加入后建议 ≥3600，以部署 yml 为准）。
- `app.security.enabled`: 是否启用管理员 JWT 与书库「公共/仅管理员」过滤（**仓库默认 yml 为 `true`**；若改为 `false` 则与旧版一致全量可见）
- `app.security.admin-username` / `app.security.admin-password`: 管理员账号密码（密码可用环境变量 `ADMIN_PASSWORD`）
- `app.security.jwt-secret` / `app.security.jwt-expiration-ms`: JWT 密钥与有效期（密钥可用环境变量 `JWT_SECRET`）

补充（任务恢复）：
- 本版本支持任务入库与启动恢复（`generation_task`）。
- 若应用重启，`PENDING/RUNNING` 任务会在启动后自动恢复执行。
- `OUTLINE_REGENERATE` 仅占位互斥：重启后若仍有残留记录，会被恢复流程收尾，避免阻塞章节类任务。

---

## 附录：联调 JSON 样例（与 web-agent「前端对接需求」对齐）

以下样例为**结构示意**（`novelId`/`taskId`/时间戳等可按实替换）。**小说域成功 Map** 均含 `status`、`code`、`message` 及扁平业务字段；**异常**走 `ApiResponse` 时仅 `code`/`message`/`data` 三键（`data` 多为 `null`）。

### A. `GET /api/novel/config/frontend-runtime`

```json
{
  "status": "success",
  "code": "OK",
  "message": "前端联调用运行时配置（无敏感信息）",
  "securityEnabled": true,
  "jwtExpirationMs": 86400000,
  "autoContinueDefaultTarget": 20,
  "autoContinueMaxTarget": 200,
  "outlineDetailedPrefixChaptersDefault": 40,
  "outlineMinRoadmapChaptersDefault": 120,
  "outlineDetailedPrefixChaptersMin": 15,
  "outlineDetailedPrefixChaptersMax": 150,
  "outlineMinRoadmapChaptersMin": 30,
  "outlineMinRoadmapChaptersMax": 600,
  "narrativeEngineEnabled": true,
  "m7ArtifactEnabled": true,
  "m9CrosscutEnabled": false,
  "outlineTwoPhaseGraphEnabled": true,
  "outlineGraphPhaseMaxTokens": 3600
}
```

### A.1 `GET /api/novel/{novelId}/narrative-state`（M9，有数据时）

```json
{
  "novelId": 1001,
  "narrativeState": {
    "version": 1,
    "schemaVersion": 2,
    "sourceChapter": 6,
    "updatedAt": "2026-05-03T12:30:00Z",
    "m4CarryoverPreview": "【上一章收束｜第6章】…",
    "latestContinuityAnchor": "结尾主角决定次日前往旧城区…",
    "chapterEntities": ["主角", "女主"],
    "chapterFactsPreview": ["主角确认线索来源"],
    "recentSidecarFacts": [
      { "chapterNumber": 5, "content": "…" }
    ],
    "relationshipHints": [
      "主角: 对女主态度软化但仍戒备"
    ],
    "latestPlotSnapshot": {
      "snapshotChapter": 5,
      "contentPreview": "- 主线节点…"
    },
    "tensionRippleHint": "距上次阶段快照较近（1 章）：伏笔与情绪余波可适当加重，避免跳档。"
  }
}
```

### B. Map 形态错误（续写 `TASK_CONFLICT`）

`POST /api/novel/1001/continue`，HTTP **200**，Body：

```json
{
  "status": "error",
  "code": "TASK_CONFLICT",
  "message": "目标章节正在重生/续写中，请稍后重试。"
}
```

### C. Map 形态错误（大纲再生 `TASK_CONFLICT`）

`POST /api/novel/1001/outline/regenerate`，HTTP **200**，Body（`message` 与并发互斥文案一致）：

```json
{
  "status": "error",
  "code": "TASK_CONFLICT",
  "message": "本书存在进行中的章节生成任务，请等待结束后再修改大纲"
}
```

### D. `STYLE_CONTINUE_TASK_FAILED`（novel-style）

`POST /api/novel-style/light-novel/1001/continue`，HTTP **200**，Body：

```json
{
  "status": "error",
  "code": "STYLE_CONTINUE_TASK_FAILED",
  "message": "服务暂时不可用，请稍后重试"
}
```

（`message` 为 `UserFriendlyExceptions.mask` 结果，**不**暴露堆栈原文。）

### E. `INVALID_STYLE_PARAMS`

`POST /api/novel/1001/writing-style-params`，HTTP **200**：

```json
{
  "status": "error",
  "code": "INVALID_STYLE_PARAMS",
  "message": "writingStyleParams 不是合法 JSON 或不包含支持的字段"
}
```

> 常见原因：Body 非 JSON、根不是 Object、或根对象 **不含 §16.2.1「受支持分支」中任意一类有效内容**（例如仅传 `{}`、或仅含未识别键）。

### F. 访客访问 `libraryPublic=false` 的书（`GET /api/novel/{id}`）

HTTP **404**，`ApiResponse`：

```json
{
  "code": 404,
  "message": "未找到该小说，请检查链接或刷新列表",
  "data": null
}
```

### G. 未登录调 `library-visibility`（须管理员）

HTTP **403**，`ApiResponse`：

```json
{
  "code": 403,
  "message": "需要管理员登录",
  "data": null
}
```

### H. `GET /api/novel/{novelId}/generation-tasks` 中单条任务（`CONTINUE_SINGLE`）

`tasks` 数组元素为 **GenerationTask 实体 JSON**（字段名与 ORM 一致：`id`、`novelId`、`taskType`、`status`、`rangeFrom`、`rangeTo`、`currentChapter`、`payloadJson`、`retryCount`、`lastError`、`startedAt`、`heartbeatAt`、`finishedAt`、`createTime`、`updateTime`）。

```json
{
  "status": "success",
  "code": "OK",
  "novelId": 1001,
  "tasks": [
    {
      "id": 501,
      "novelId": 1001,
      "taskType": "CONTINUE_SINGLE",
      "status": "RUNNING",
      "rangeFrom": 6,
      "rangeTo": 6,
      "currentChapter": 5,
      "payloadJson": "{\"generationSetting\":null,\"chapterNumber\":6}",
      "retryCount": 0,
      "lastError": null,
      "startedAt": "2026-05-03T10:00:00",
      "heartbeatAt": "2026-05-03T10:02:00",
      "finishedAt": null,
      "createTime": "2026-05-03T09:59:00",
      "updateTime": "2026-05-03T10:02:00"
    }
  ]
}
```

**`taskType` 取值**：`INITIAL_BOOTSTRAP`、`CONTINUE_SINGLE`、`AUTO_CONTINUE_RANGE`、`REGENERATE_RANGE`、`OUTLINE_REGENERATE`（大纲占位租约）。**`status`**：`PENDING`、`RUNNING`、`DONE`、`FAILED`、`CANCELLED`。

### I. `OUTLINE_REGENERATE` 占位任务（示意）

```json
{
  "id": 502,
  "novelId": 1001,
  "taskType": "OUTLINE_REGENERATE",
  "status": "RUNNING",
  "rangeFrom": 0,
  "rangeTo": 0,
  "currentChapter": 0,
  "payloadJson": "{}",
  "retryCount": 0,
  "lastError": null,
  "startedAt": "2026-05-03T11:00:00",
  "heartbeatAt": "2026-05-03T11:00:01",
  "finishedAt": null,
  "createTime": "2026-05-03T11:00:00",
  "updateTime": "2026-05-03T11:00:01"
}
```

### J. `INITIAL_BOOTSTRAP`（开书）

```json
{
  "id": 1,
  "novelId": 1002,
  "taskType": "INITIAL_BOOTSTRAP",
  "status": "PENDING",
  "rangeFrom": 1,
  "rangeTo": 5,
  "currentChapter": 0,
  "payloadJson": "{}",
  "retryCount": 0,
  "lastError": null,
  "startedAt": null,
  "heartbeatAt": null,
  "finishedAt": null,
  "createTime": "2026-05-03T08:00:00",
  "updateTime": "2026-05-03T08:00:00"
}
```

### K. M7 `GET /api/novel/{novelId}/narrative-artifacts`

**有数据**（`artifact` 为解析后的对象）：

```json
[
  {
    "chapterNumber": 3,
    "chapterTitle": "第3章 试炼",
    "artifact": {
      "version": 1,
      "chapterNumber": 3,
      "capturedAt": "2026-05-03T12:00:00Z",
      "planner": { "status": "skipped", "skipReason": "two_phase_disabled" },
      "lint": { "status": "clean", "hitCount": 0, "issues": [] },
      "critic": { "status": "skipped", "skipReason": "m8_critic_disabled" }
    }
  }
]
```

（`planner`/`lint`/`critic` 字段随版本与开关变化，以前端容错解析为准。）

**仅 `artifactRaw`（库内 JSON 非法时兜底）**：

```json
[
  {
    "chapterNumber": 2,
    "chapterTitle": "第2章",
    "artifactRaw": "{invalid"
  }
]
```

**空列表**：

```json
[]
```

### L. `GET /api/novel/{novelId}` 实体字段示意（脱敏）

响应为 **Novel 实体 JSON**（非 Map 壳）。常用字段：

```json
{
  "id": 1001,
  "title": "示例书",
  "topic": "修仙题材",
  "generationSetting": "第三人称，节奏偏快",
  "description": "（大纲正文，可能很长）",
  "outlineGraphJson": "{ \"schemaVersion\": 1, \"engine\": \"conflict_escalation\", \"cast\": [ ... ], ... }",
  "writingPipeline": "LIGHT_NOVEL",
  "writingStyleParams": "{\"dialogueRatioHint\":\"high\",\"narrativePhysicsMode\":\"continuous\"}",
  "hotMemeEnabled": false,
  "libraryPublic": true,
  "serializationPlatform": "未发布",
  "creatorNote": "联调样例",
  "narrativeCarryover": null,
  "outlineDetailedPrefixChapters": 40,
  "outlineMinRoadmapChapters": 120,
  "createTime": "2026-05-01T08:00:00",
  "updateTime": "2026-05-03T09:00:00",
  "writePhase": "IDLE",
  "writeRangeFrom": null,
  "writeRangeTo": null,
  "writeCursorChapter": null,
  "writePhaseDetail": null,
  "writeStartedAt": null,
  "writeUpdatedAt": null,
  "userId": null,
  "groupId": 0
}
```

**说明**：`writingStyleParams` 为 **字符串**（内层 JSON 需前端 `JSON.parse`）；`writingPipeline` 一般为 **枚举名字符串**（与 `GET .../pipeline` 的 `pipeline` 字段一致）。

