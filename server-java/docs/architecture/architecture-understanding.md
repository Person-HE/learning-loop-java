# server-java 架构理解报告（当前态 · 详细设计说明）

> 产出方式：`system-modeler` + `c4model` + `graphviz`
> 视角状态：**current-state**（重构实现已完成并运行时验证，非目标态提案）
> 生成日期：2026-09-19 ｜ 读者：开发者本人 + 未来接手的 AI/工程师
> 阅读顺序：§1 边界 → §2 业务能力/领域模型 → §3 容器 → §4 分层与模块职责 → §5 AI 工程化 → §6 状态与并发 → §7 数据拓扑 → §8 关键流程 → §9 契约面 → §10 置信度与待验证项
> 图形工件：`learning-loop.structurizr.dsl`（C1/C2/C3 三视图）、`system-dependencies.dot`（包依赖实测图）、`learning-loop-path.dot`（端到端业务主路径）；证据索引见 `system-model.evidence.md`
>
> **⚠ 2026-09-24 勘误（本文件生成于 09-19，此后代码三次大进化，逐条清单见 `../../../实战手记/_audit/一致性审计-20260924.md`）**：
> ① 主存储已改 MySQL 8 `learning_loop`（`STORAGE_MODE` 默认 mysql），§3/§6/§7 中「state.json 唯一事实源/双后端共写」仅对 json 模式成立；
> ② 新增交互式两步选题（`learning/PlanBuilder` + `/api/today/plan|confirm`）与队列分模块输出；
> ③ 新增 RAG 链（`web/RagController`、`application/rag`、`infrastructure/{rag,ai}`、embedding sidecar :3003、Qdrant 配置）——§9「30 端点与 Node 1:1」前提已破：plan/confirm/rag 为 Java 独有；
> ④ weekly 场景挂 60s 专属 per-call 截止（`AiCall.withTimeoutSeconds`）；
> ⑤ 文件计数 60→94（8740 行），web 9→13、learning 5→6。§1-§2、§4 依赖方向、§5 AI 工程化主体结构仍成立。

---

## 1. 系统边界（L1 System Context）

**系统是什么**：面试知识闭环学习系统的 Java（Spring Boot）后端重构实现，与 Node 版 `server/` 行为逐一对齐、共享数据、并存运行。

- **唯一用户**：学习者本人（单机自用，无登录/多租户/权限体系——代码中不存在任何鉴权，这是设计事实而非缺失）。
- **直接协作者**：
  - `React 前端（client/）`：HTTP JSON 调用 `/api/**`，snake_case 契约，前端零改动。
  - `AI 服务（OpenAI 兼容）`：`/v1/chat/completions`，是本系统全部智能能力的唯一上游。
  - `后端知识库（Markdown 文件树）`：域/章/文档/assets，**只读**扫描，是知识点注册表的事实源。
  - `Node 版后端（server/）`：共享 `state.json`/`lc100.json` 数据文件，代码互不修改。
- **边界内**：`server-java` 单进程（端口 3002）+ 其读写的 4 个本地数据源。
- **配置事实源**：`src/main/resources/application.yml`（kb-root、data-dir、resume-html、client-dist、kb-text-limit、app.ai.*）。

## 2. 业务能力与领域模型（L0）

### 2.1 能力地图（全部有代码证据）

| 业务能力 | 承载模块 | 一句话说明 |
|---|---|---|
| 今日学习队列编排 | QueueBuilder + QuestionService + StateViewService | 同题复答→到期复习→新知识点（权重最大余数法）→缺口补测，极简模式 maxQ=1 |
| AI 出题 | QuestionService + KbPrompts | 按知识点文档+锚点生成 T1-T4 题，生成锁串行化 |
| AI 评分 | GradingService + score prompt | 标准要点/逐点比对/改写对比/优化答案/建议（review/practice/extend） |
| 复述打分 | RestateService | 覆盖点/遗漏点/覆盖率百分比 |
| 间隔重复调度 | Sm2 | SM-2 修订 + FSRS 双因子 R=e^(−Δt/S)，due 计算 |
| 缺口闭环 | GapService | 评分<60 产生缺口→AI 补测题→≥75 自动 resolve |
| 记忆曲线分析 | CurveService + Kpi | 单点曲线、全班掌握度中位数/平均 R/到期榜 |
| 力扣刷题 | LcService + LcPrompts | 101 题清单/精讲缓存/代码练习/AI 追问 |
| 拿分要点 | KpPlanService | 文档→要点+记忆卡片，已掌握点按 ≥75 作答反标 |
| 项目深挖面试 | ProjectInterviewService | 简历切面 f-all/f1-f5 出题→评分→facet 复习调度 |
| 模拟面试 | MockInterviewService | 5 问状态机（L0-L4 分级），结束出总评 |
| 周报 | WeeklyService | 统计文本→AI strengths/weaknesses/focus |
| 简历体检 | resumeScore | 维度分/问题/建议/亮点，缓存于 project.resumeScore |

### 2.2 核心领域实体（`state/model` 持久化 DTO，业务规则全部在 `learning` 包）

- **Kp（知识点）**：id/domain/chapter/title/difficulty/hot/prerequisites/anchors/assets/path/kbStatus/category + 学习态（status、SM-2 三参数、due、history）。启动时由 `knowledge` 扫描结果**合并**进 state（新增补齐、失效标注），路径含域前缀（与 Node kb.js 对齐，见修复记录）。
- **QueueItem**：今日队列条目（kpId、kind=kb/lc/gaptest/replay、reason、questions、genError）。
- **AnswerRecord**：一次作答（分数、等级、gaps、复述结果、stall 标记）。
- **Gap**：缺口（label、kpId、resolved），生命周期：产生→补测→resolve。
- **SessionToday / SessionDay**：今日会话计数与历史归档（rollover 时结转）。
- **ProjectState**：facets 复习态（due/history slice(-20)）、sessions（cap 500）、resumeScore、深挖题缓存 cache、模拟面试进行时会话 mock。
- **Settings**：weights(java40/algo30/ai30)、mode(standard/minimal)、frozen/freezeDays(14)、missedDays、maxQueue、firstUseDate；apiKey 下发时打码 `****+后4位`，`aiLocked:true`。
- **辅助态**：`cursor`（三类进度指针）、`genSessions`（直接出题临时会话，24h 过期）、`gapTests`（一次性补测题）、`lcExplains`（精讲缓存）。

### 2.3 生命周期（结构性证据 + 行为证据均齐）

知识点：`new → 出题 → 作答评分 → SM-2 排期 → 到期复习 →（<80 触发同题复答）`；
学习日：`跨天 ensureToday() → 归档昨日 SessionDay → missedDays++ / 达标归零 → 极简模式升降档（minimalStreak）→ 14 天冻结`；
模拟面试：`start(qCount=1) → turn×N（finished→qCount=max）→ end（总评+MS- 会话+facet 回写）`。

## 3. 容器视图（L2）

单部署单元：**apiApp（Spring Boot 进程, :3002）**。2026-09-24 起存储双模式（`app.storage.mode=${STORAGE_MODE:mysql}`）：**MySQL 8 `learning_loop` 为主存储**（kp/kp_state/answer_record/question_hist/queue_item/gap/session_day/app_setting + rag_doc/rag_chunk），向量检索经 LangChain4j 配置接 Qdrant（gRPC 6334）与 embedding sidecar（:3003），属外部依赖；**json 模式下回退单文件存储**：

| 容器 | 角色 | 读写模式 |
|---|---|---|
| MySQL 8 `learning_loop`（mysql 模式，默认） | 全局状态主存储（`application/store/MySqlStateStore`：装载全量读 + saveAfterAnswer 等热写分表） | HikariCP 连接池；读路径带 `[store-read]`/`[store-selfcheck]` 探针 |
| state.json (v2) | json 模式事实源；mysql 模式下仅为**导出/备份件** | Java：tmp+原子 rename；与 Node 共享（json 模式） |
| lc100.json / lc-solutions.json | 题库题面/题解 | Java 只读（Node 侧可写） |
| 后端知识库 .md/assets | 知识源 | 只读；assets 经白名单+防穿越由 `/kb-assets/**` 代理 |
| resume_upload.html | 简历预览 | 只读 |
| client/dist | 生产前端 | 存在才挂载 `/**`（`StaticResourceConfig`） |

进程内缓存：KB 扫描结果（volatile + synchronized 双检）；lcExplains/kpPlan 等**持久化型缓存** json 模式写 state.json、mysql 模式写 `kp_state.plan_json` 等列，重启不丢。

## 4. 分层架构与模块职责（L3，核心设计）

依赖方向只允许 `web → service → {learning | ai | knowledge | project | state}`，`config/common` 为横向基础设施。实测 import 计数图见 `system-dependencies.dot`。

### 4.1 各层职责与内聚手段

- **web（契约层，13 文件）**：每个 Controller 只做「取参（Req 助手）→ 调一个 service 方法 → 返回」。零业务分支。错误统一抛 `ApiException`，`GlobalExceptionHandler` 渲染 `{"error":msg,"code?":...}`（与 Node 错误形状一致）。`RolloverInterceptor` 把「每请求跨天滚动」这个横切关注点从所有 service 里抽出来。09-24 新增：`QueueController` 的 `/api/today/plan|confirm` 两步选题端点、`RagController` 语义检索端点。
- **service（编排层，11 文件）**：每个用例一个服务、一文件一类问题的内聚切分。负责：输入校验→取上下文（knowledge/state）→组 prompt 调 ai→规范化结果→`state.mutate` 回写→组装响应。**所有响应 Map 用 `Utils.m(...)`（LinkedHashMap）保证字段顺序与 Node 契约逐字节对齐**。
- **learning（机制内核，6 文件）**：SM-2/FSRS、队列组装、作答回写、KPI 全部为**纯函数式**（输入 AppState + 参数，原地更新或直接算），不 import web/ai/config——可脱离 Spring 单测，是系统里唯一"懂学习理论"的地方。09-24 起 `QueueBuilder` 拆「只算不写」供新增 `PlanBuilder`（交互式两步选题）复用。
- **state（持久化编排，9 文件）**：模型是公开字段 DTO（`@JsonIgnoreProperties(ignoreUnknown=true)` 向前兼容 Node 写入的未知字段）；并发控制集中在 StateManager 一个类。实际读写后端按 `STORAGE_MODE` 分流：json→StateRepository 原子文件写，mysql→`application/store/MySqlStateStore`（application/ 包 2 类、infrastructure/ 包 18 类：persistence 实体+mapper、rag、ai 嵌入）。
- **ai / knowledge / project**：见 §5、下段。

### 4.2 三条受控例外依赖（显式记录，非腐化）

| # | 边 | 事实 | 为什么可接受 |
|---|---|---|---|
| X1 | `ai.prompt → state.model / project / knowledge`（10 imports） | Prompt 模板签名直接引用 `Kp`、`QueueItem.Question`、`ResumeData.Facet`、`LcProblem` 等**类型** | 仅消费只读记录类型做上下文注入，不触达 StateManager、不产生状态突变；换来的是 prompt 构建零 Map 弱类型 |
| X2 | `state → knowledge`（StateManager 3 imports） | 启动/refresh 时把 KB 元信息合并进 kpStates | 合并是 state 生命周期的一部分；反向依赖（knowledge 感知 state）才是违例。若需洁癖化，可提 `KpMerger` 到 service 层，收益低 |
| X3 | `config.WebConfig → web.RolloverInterceptor` | CORS 配置注册拦截器 | Spring 惯常做法；如在意可让拦截器自注册（@Component + WebMvcConfigurer 收集），当前无必要 |

### 4.3 类规模（内聚度旁证）

94 个 Java 文件、8740 行（2026-09-24 实测；成文时 60 个）、无 God Class：最大的是 `StateViewService`/`QueueBuilder`/`MySqlStateStore`（400-490 行级），平均约 120 行；prompt 按域拆 4 文件避免模板字符串堆积。

## 5. AI 工程化（本系统最重要的设计资产）

**唯一出口 `ai/AiClient`**（JDK HttpClient，无第三方 HTTP 依赖）：

```
AiCall(参数对象: messages/temperature/maxTokens/validator/maxRounds)
  └─ chatJson：调用 → JsonExtractor → validator
        不合格 → assistant 回显 + 2 条固定中文纠错提示 → 重试（≤3 轮，maxTokens ×1.5，上限 8000）
  └─ chat：纯文本（LC 追问、模拟面试语音化台词）
  └─ 传输层：500ms×2^attempt+抖动，maxRetries=3
        AUTH(401) → 立即抛出永不重试；RATE(429)/UPSTREAM/NET → 退避重试
        错误码契约：AUTH / RATE / UPSTREAM / EMPTY / NET / FORMAT
```

- **JsonExtractor 三级容错**：剥 ```json fence → 括号配平截取 → 尾逗号修复。`FormatReject` 承载拒绝原因回喂。
- **validator 是领域合同**：每个调用点声明必填形状（如评分要求 `total_score` number + `standard_points`/`point_compare` 非空数组），杜绝"AI 返回什么就存什么"。
- **规范化层（GradingService.normalizeScore 等）**：clamp/裁剪/白名单（status、level∈{review,practice,extend}、improve 前缀正则、read≤120 字、practice≤160 字、LC 分支强制 advice+lcNo），保证入库与下发的都是干净 snake_case 契约。
- **prompt 模板库（`ai/prompt/`）**：`Tpl`（小工具）、`Dims`（评分维度/缺口标签）、`KbPrompts`/`LcPrompts`/`ProjectPrompts`/`MockPrompts`（按域）、`ResumeContext`（简历语境注入）。service 层不出现任何 prompt 字符串。

## 6. 状态与并发模型

`state/StateManager`（全系统唯一写路径）：

- **withGenLock（ReentrantLock）**：AI 出题/缺口出题/评分生成期间持锁；拿不到锁 → 400「正在生成题目，请稍候…」。防止生成期间用户改队列。
- **mutate(Supplier) / mutateVoid(Runnable)**：synchronized 读-改-写 + `StateRepository.save`（tmp→ATOMIC rename）；解析失败先 `.bak-时间戳` 备份再重建，绝不静默覆盖。
- **ensureToday()**：由拦截器在**每个 API 请求前**幂等触发（非定时任务），跨天才变更——单机自用的正确取舍。
- **已知假设**：Node 与 Java **同时**写 state.json 无跨进程锁（进程内锁互不可见）。设计意图是"二选一运行"；混跑最后写入者胜。→ 待验证项 V4。

## 7. 数据拓扑（谁拥有哪份数据）

| 数据对象 | Owner | 流 |
|---|---|---|
| state.json | 双后端共享（Node 先建 schema v2） | Java 全量读、原子写 |
| 知识点注册表（kps 元信息） | 知识库文件树 | KB 扫描 → 启动合并进 state → 前端地图 |
| 学习态（SM-2 参数/due/history） | state.json 独占 | AnswerApplier 唯一写路径 |
| lc100/solutions | Node 侧维护 | Java 只读 |
| 各类 AI 缓存（kpPlan/lcExplains/project.cache/resumeScore/genSessions/gapTests） | service 层写、state 层存 | 过期：genSessions 24h 剪枝；cache 按 facetId 失效；cap：sessions 500、facet history 20、records 下发 200 |
| AI 密钥 | application.yml 内置（设置页只读打码，`aiLocked:true`，运行时不可覆盖） |

## 8. 关键流程（业务语言版）

主闭环见 `learning-loop-path.dot`：打开 → 队列组装（四级优先级）→ 并行 AI 出题 → 作答 → AI 评分 → SM-2 回写 → 缺口分支 → 曲线/周报复盘 → 明日 due。三条子主路径同构：力扣（练习=代码题合成 + 精讲缓存 + 追问）、项目深挖（切面→题缓存→评分→facet due 7/3/1/0 天）、模拟面试（5 问状态机→总评→MS- 会话入库）。

## 9. 契约面（REST，30 端点，与 Node 1:1）

见 `server-java/README.md` 端点表。契约保真三原则：响应键序（Utils.m）、错误形状（`{"error","code?"}`）、AI 结果 snake_case 透传键名。开发模式 CORS `/api/**` 全开放（单机自用），生产同源托管 dist。

## 10. 置信度与待验证项

所有结构断言均可回溯到源码（见 evidence 索引，全部 high）；运行时验证已完成：health/state/queue build/kb/lc/curve/records/projects + **真实 AI 调用**（kp/points、questions/gen，2026-09-19 实测通过）。

| # | 待验证 | 方法 |
|---|---|---|
| V1 | 评分/复述/精讲/周报/模拟面试等其余 AI 端点尚未逐个实跑（代码同构，链路已证） | 前端过一遍或 curl 逐点 |
| V2 | 缺口补测 ≥75 自动 resolve 的实机路径 | 造一次 <60 评分后跟补测 |
| V3 | KB `POST /api/kb/refresh` 的 added/totalDocs 增量语义 | 新增一篇文档后 refresh |
| V4 | Node+Java 并发写 state.json 的丢更新 | 明确"二选一运行"约定即可，无需代码改动 |
