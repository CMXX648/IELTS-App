# VoxCoach — 编码辅助指令文件

> 本文件用于 agy cli（AGENTS.md）与 Claude Code（CLAUDE.md），两者内容一致。
> 开发方向与详细设计已在 `docs/`（01–06）冻结（M0 设计评审通过）。编码从 **M1 工程骨架 + 链路验证**起步，详见 @docs/06-roadmap-acceptance.md §1–2。

## 1. 项目定位（方向红线）
个人自用的 Android 英语口语练习 App（开发代号 **VoxCoach**），核心目标：辅助达成**雅思总分 7.5**。
技术主线：**ASR + TTS + LLM 实时口语对话** + **基础语法句式训练** + 官方四维评分反馈 + 录音复盘 + 错题本/档案，数据可同步至自有 2 核 2G 服务器。

## 2. 技术栈与版本基线
- 端：Kotlin + Jetpack Compose + Material 3；**compileSdk/targetSdk 37（Android 17）、minSdk 26**；MVVM + Repository + Hilt + Room，模块分层 `feature:*` → `core:*` 单向依赖。
- 语音：ASR/TTS 一律走抽象接口（`core:speech`），默认 **MiMo-V2.5**（`mimo-v2.5-asr` / `mimo-v2.5-tts`），与对话共用设置中的 Base URL + API Key；会话录音本地 wav，**不同步到自有服务器**（识别片段会发往用户配置的 MiMo 端点）。
- LLM：OpenAI 兼容接口（`core:llm`），对话默认 `mimo-v2.5`（设置页不手填模型），SSE 流式；Key 存 Android Keystore，永不明文落盘。
- 服务端（可选/后期）：Go + SQLite，只做多设备**增量同步**与可选 **LLM Key 代理**，不跑大模型、不存音频。
- 工具链版本须按 @docs/04-android-architecture.md §1.2 核对后冻结于 `libs.versions.toml`。

## 3. 统一术语（与 @docs/README.md 术语表一致）
- 模块：**CV** 对话练习 / **GR** 语法句式 / **EV** 四维评测 / **RP** 录音回放 / **VB** 错题本 / **PF** 档案统计 / **ST** 设置 / **SY** 同步。
- 实体：Session、Turn、EVResult、FeedbackItem、GrammarPoint、Topic、Stage(S0–S4)、BandScore。
- 功能需求 ID 以 @docs/01-prd.md §3.2 为准（如 CV-01）；MVP = P0 集合（§4），P1 属 M3，**勿擅自扩大范围**。

## 4. 文档读取纪律（渐进式，禁止整读）
docs 文件较长且已冻结，**禁止一次性全文读取/注入**任何 docs 文档。一律：
1. 先读本文件 + 仓库根目录 `README.md`（含「团队约定：MiMo-V2.5」与术语表）。
2. 按任务需要先定位文档与**目标小节**，再用 `read` 带 offset/limit 分段精读，或交给 code-explorer 子代理检索，需要哪节再 `@` 哪节。
3. 分文档用途（建议 `@` 提及方式）：
   - 需求与 MVP 边界 → @docs/01-prd.md §3–4
   - 学习路径/语法点/话题/Stage → @docs/02-learning-path.md §3–5
   - 页面交互与状态机 → @docs/03-feature-spec.md（按模块 §2–§6）
   - Android 实现/建库/权限 → @docs/04-android-architecture.md（如 §2 模块、§5 LLM、§6.1 Room、§7.2 适配）
   - 后端 API/同步/ER → @docs/05-backend-api.md（如 §4.2 同步、§4.4 EVResult、§6 Key 代理）
   - 里程碑/验收 → @docs/06-roadmap-acceptance.md §1–2

## 5. 工程铁律
1. **文档即真源**：实现若与 docs 冲突，须先修订 docs 或说明偏离理由，再继续编码。
2. **不加戏**：不引入 docs 之外的框架/大依赖；新需求先改设计（修订 docs）再实现。
3. **隐私默认**：会话 wav 仅本地；识别/TTS 走用户配置的 MiMo；自有云同步显式开启且不存音频；一切 Key/.env 不入版本库。
4. **抽象边界**：ASR / TTS / LLM / 存储 / 同步接口保持可替换、可注入 mock；禁止跨层直连（如 ViewModel 直接 new 引擎）。
5. **数据一致性**：实体字段、API 字段、Room 表三处命名对齐（见 @docs/04 §6.1 与 @docs/05 §4）。
6. 每次改动后自查 lint/编译错误；状态机与核心算法必须可单测。

## 6. 行为守则
- 小步精确修改，不整段重写既有文件。
- 未经请求不 `git commit`、不改配置、不擅自加依赖/临时文件。
- 无真机语音环境时，用可注入 mock 保证链路可测，不伪造演示数据糊弄功能。
- 任务收尾清理临时产物。
