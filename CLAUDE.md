# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

称呼用户为 **归儿** 或 **归归**。

## Build & Run

```bash
# Windows PowerShell — local dev (SSH tunnel required for DB)
$env:SPRING_DATASOURCE_PASSWORD='***'
$env:OPENAI_API_KEY='sk-***'
$env:OPENAI_BASE_URL='https://api.meai.cloud'
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Deploy to cloud server (154.8.213.134)
bash note/deploy.sh

# Package only
mvn package -DskipTests -q
```

- **JDK 17** at `D:\JDK`; default `java` on PATH is JDK 8 — use `D:\JDK\bin\java` explicitly.
- **Local profile** uses MySQL on port `3307` (SSH tunnel: `ssh -L 3307:localhost:3306 -N -f alice@154.8.213.134`).
- **Cloud deploy** uses MySQL on `127.0.0.1:3306` directly, JAR placed at `/opt/qq-bot/novel-agent-1.0-SNAPSHOT.jar`.
- Windows page file is small — use `-Xmx512m -XX:+UseSerialGC` when running `java -jar` directly.

## Architecture

This is an **AI-powered web novel (网文) generation system** — Spring Boot 3.2.4 + MySQL + Claude API (via Spring AI OpenAI-compatible client, proxied through `api.meai.cloud`).

### Package Map

| Package | Role |
|---------|------|
| `agent/` | LLM call wrappers: `NovelGenerationAgent` (orchestrates draft→review→polish pipeline), `ContentReviewAgent`, `ConsistencyReviewAgent`, `PolishingAgent` |
| `service/` | Business logic. **`NovelAgentService`** is the core — `bootstrapNovel()` (initial creation) and `genInner()` (single chapter generation). Other services provide prompts, memory, consistency checks. |
| `controller/` | REST endpoints. `NovelManagementController` (`/api/novel`) is the main HTTP surface (~60 endpoints). `QQMessageController` handles NapCat bot webhooks. |
| `model/` | JPA entities. `Novel` is the aggregate root; `Chapter` has content + metadata. All relationships are plain `Long` foreign keys — no JPA `@OneToMany`. |
| `prompt/` | `NarrativeCraftPrompts.java` (~1600 lines) — block-composition prompt engineering. Every prompt function accepts `WritingPipeline` to branch per genre. |
| `narrative/` | M1-M9 narrative engine: profile resolution, linting, critic, carryover, cross-chapter snapshots. |
| `config/` | `AiConfig` creates Spring AI beans; `GenerationTaskRecoveryRunner` resumes pending tasks on startup. |
| `security/` | JWT auth (optional, controlled by `app.security.enabled`). 3-tier access: ADMIN > USER > guest. |
| `repository/` | Spring Data JPA interfaces. Use `@Query` for complex updates; prefer COUNT queries over loading full entities when only counting is needed. |
| `qq/` | QQ/NapCat bot integration: command parser, dispatcher, group access service. |

### Chapter Generation Pipeline

1. **Draft**: assemble prompt from `NarrativeCraftPrompts` blocks (narrator persona → outline → character profiles → style constraints → M1-M6 narrative engine), call LLM, retry up to 3x if content too short.
2. **Consistency Review**: separate LLM call checking setting/character/timeline consistency.
3. **Content Review**: safety/compliance scan.
4. **Polish**: style-specific prose polishing (can be disabled via `novel.generation.polish-enabled`).
5. **Post-generation**: sidecar extraction (title/facts/entities), name consistency auto-fix, character state delta, plot snapshot refresh, M4 carryover update, M9 cross-chapter snapshot.

### Async Task Model

All generation operations are **async** — they create a `GenerationTask` record (status: `PENDING → RUNNING → DONE/FAILED/CANCELLED`), then execute via `CompletableFuture`. Tasks survive process restart via `GenerationTaskRecoveryRunner`. Concurrency is controlled by DB-level task locks (`TASK_CONFLICT` returned on overlapping intervals).

### Writing Pipeline Enums

`WritingPipeline`: `POWER_FANTASY` (爽文, default), `LIGHT_NOVEL` (轻小说), `SLICE_OF_LIFE` (日常), `PERIOD_DRAMA` (年代文), `VULGAR` (粗俗). Aliases resolved by `WritingPipeline.fromPath()` — unrecognized strings silently fall back to `POWER_FANTASY`.

### v2.0 Features (May 2026)

New services integrated into generation flow:
- `StrandWeaveService` — assigns QUEST/FIRE/CONSTELLATION strand per chapter with hard constraints (Quest ≤5 consecutive, Fire ≤10 gap, Constellation ≤15 gap)
- `ForeshadowingService` — buried→reminded→paid_off lifecycle with urgency auto-escalation
- `StoryContractService` — MASTER_SETTING → VOLUME → CHAPTER → REVIEW → CHAPTER_COMMIT provenance chain
- `ChapterCommitService` — accepted/rejected commit audit trail
- `AiFlavorDetector` — 5-dimension anti-AI detection (vocabulary/sentence/narrative/emotion/dialogue)
- `ContextAgentService` — pre-writing 5-section brief
- `DataAgentService` — post-writing structured extraction
- `WritingKnowledgeService` — RAG retrieval from 9 CSV knowledge tables
- `StructuredOutlineService` — CBN/CPN/CEN chapter node parsing

Integration hooks are in `NovelAgentService.genInner()` and `bootstrapNovel()` — post-save calls to strand assignment, commit creation, foreshadowing escalation. All wrapped in try-catch so failures don't block generation.

### Database

- MySQL `novel_agent`, Hibernate `ddl-auto: update` (auto-creates tables, no Flyway/Liquibase).
- All new v2.0 tables (`story_contract`, `foreshadowing`, `chapter_reading_power`, `writing_knowledge`) auto-created on startup.
- `writing_knowledge` needs `FULLTEXT INDEX` for BM25 search — added via native SQL query in repository.

### Frontend

- **Electron app**: `D:\解压\web-agent\web-agent` — Vue 3 + Element Plus single-file SPA (580KB `index.html`). Connects to cloud API. Start with `npm start`.
- **React dashboard**: `src/main/frontend/` — Vite + React 18 + ECharts 5, builds to `src/main/resources/static/`. Pixel-art retro theme, 6 pages. Build: `npm install && npm run build`.
- **Legacy director console**: `src/main/resources/static/novels.html` — simple HTML page, restored from git (not to be deleted).

## Key Conventions

- **Entities**: Lombok `@Data`, no JPA relationships, manual `@PrePersist`/`@PreUpdate` for timestamps. Enums stored as `String` fields with default values in `columnDefinition`.
- **API responses**: Map format `{"status":"success","code":"OK","message":"..."}` for write operations. `ApiResponse` wrapper for global error handling. Non-public novels return 404 (not 403) to avoid leaking existence.
- **Constructor injection** preferred; use `@Autowired` field injection for v2.0 services added to the already-bloated `NovelAgentService` constructor.
- **Chinese strings everywhere** — prompts, API messages, enum aliases, model field comments. Bash/curl with Chinese text needs `--data-binary @file` to avoid UTF-8 corruption.
- **No test directory** — `src/test/` doesn't exist. `-DskipTests` is always needed for Maven builds.
