# 学习闭环系统 · Java 后端（Spring Boot）

Node 版 `server/` 的**行为对齐重构实现**：REST 契约、响应字段顺序、AI 容错语义与 Node 版逐一对齐，
前端 `client/` 无需任何改动即可直连本服务。两套后端**共享同一份数据文件**（`../server/data/`），学习进度实时互通。

- 端口：`3002`（Node 版为 3001，Vite 代理改 target 即可切换）
- 技术栈：Java 17 · Spring Boot 3.3.5 · Maven · Jackson · JDK HttpClient（零额外 HTTP 依赖）
- 约束：本目录为独立新增工程，**不修改** `server/` 与 `client/` 任何文件

## 架构分层（高内聚 / 低耦合）

```
web/        REST 控制器（仅参数校验+组装响应，无业务逻辑）
  │         RolloverInterceptor：每个 /api 请求前执行 ensureToday() 日滚动
  ▼
service/    业务编排（Queue/Grading/Restate/KpPlan/Gap/Curve/Weekly/Lc/
  │         StateView/ProjectInterview/MockInterview）
  ▼
learning/   纯算法层（SM-2 调度、Priority、QueueBuilder、AnswerApplier、Kpi）
ai/         AI 工程化层（见下节）——仅依赖 config，被 service 注入
knowledge/  知识库只读扫描（KbService/KbScanner/LcDataService）
project/    简历与项目面试静态数据（ResumeData）
state/      状态持久化（StateRepository + StateManager 并发守卫 + model/* POJO）
config/     AppProperties / AiProperties / WebConfig
common/     Utils / Dates / ApiException / GlobalExceptionHandler
```

依赖只允许自上而下；`learning`、`ai`、`knowledge`、`project`、`state` 为叶子层，互不引用业务编排层。

## AI 工程化（核心亮点）

`ai/AiClient` 是唯一出口，OpenAI 兼容 `/chat/completions`（JDK HttpClient）：

- **指数退避重试**：`500ms×2^attempt+抖动`，最多 3 次；`AUTH(401)` 永不重试，`RATE(429)/UPSTREAM/NET` 分类错误码
- **结构化输出保障**：`chatJson(...)` = 调用 → `JsonExtractor`（剥 ```fence、括号配平、尾逗号修复）→ 领域 validator 校验 →
  不合格则带 assistant 回显 + 2 条固定中文纠错提示，最多 3 轮自修复，maxTokens ×1.5（上限 8000）
- **Prompt 模板库**：`ai/prompt/` 按域分文件（KbPrompts/LcPrompts/ProjectPrompts/MockPrompts），
  模板、评分维度（Dims）、小工具（Tpl）集中管理，service 层零 prompt 字符串
- **契约保真**：AI 结果字段保持 snake_case（`total_score`、`standard_points`…），规范化/裁剪逻辑对齐 Node 版

## 状态并发模型

`state/StateManager`：

- `ReentrantLock` 生成锁 `withGenLock`（出题期间禁止改状态，冲突返回 400「正在生成题目，请稍候…」）
- `mutate(Supplier)` / `mutateVoid(Runnable)`：同步读-改-写 + 原子落盘 `state.json`（v2，与 Node 共用）
- `ensureToday()`：跨天滚动（补写 missedDays、冻结检查、到期队列刷新），由拦截器按请求触发

## 端点一览（与 Node 版 1:1）

| 域 | 端点 |
|---|---|
| 系统 | `GET /api/health` · `GET /api/state` · `GET /api/records` · `POST /api/records/{id}/stall` · `POST /api/settings` · `POST /api/state/reset` |
| 知识库 | `GET /api/kb` · `POST /api/kb/refresh` · `GET /api/kb/doc` · `GET /kb-assets/**` |
| 队列 | `POST /api/queue/build` · `POST /api/queue/prepare` · `POST /api/questions/gen` |
| 学习闭环 | `POST /api/answer/score` · `POST /api/restate/score` · `GET /api/kp/points` |
| 缺口 | `GET /api/gaps` · `POST /api/gaps/{id}/test` · `POST /api/gaps/{id}/resolve` |
| 记忆曲线 | `GET /api/curve` · `GET /api/curve/summary` · `POST /api/weekly` |
| 力扣 | `GET /api/lc` · `GET /api/lc/{no}` · `POST /api/lc/{no}/explain` · `POST /api/lc/{no}/practice` · `POST /api/lc/{no}/ask` |
| 项目面试 | `GET /api/projects` · `POST /api/projects/questions` · `POST /api/projects/score` · `POST /api/projects/mock/start|turn|end` · `POST /api/projects/resume-score` |

## 构建与运行

```bash
cd server-java
mvn package -DskipTests
java -Dfile.encoding=UTF-8 -jar target/learning-loop-server-1.0.0.jar
```

> 注意：本机（中文路径 + Windows）下 `mvn spring-boot:run` 因 fork classpath 问题会报
> 「找不到主类」，请始终用 `java -jar` 方式启动。

配置集中在 `src/main/resources/application.yml`（`app.kb-root`、`app.data-dir`、`app.ai.*`）。
AI key 只从环境变量 `AI_API_KEY` 注入（仓库内无明文回退），设置页只读展示，运行时不可覆盖（`aiLocked:true`）。

---

## 企业级工程化（2026-09-22 落地）

### 提示词工程 Prompt Engineering as Code
- ai/prompt/PromptRegistry：15 个 scene 的版本 / temperature / maxTokens / schemaVersion / status
- 审计写入 promptRef（如 kb-score@v3.0），模板改动可归因
- Dims 评分维度集中管理；面试问法铁律 / 手写代码题 T6 仍在 KbPrompts

### 上下文工程 Context Engineering
- ai/context/ContextPlan + TokenEstimator：槽位预算装配（SYSTEM / TASK / DOC / PROFILE…）
- 超预算从低优先级裁剪，digest() 记录 used/dropped
- 评分链路（GradingService）已接入 ContextPlan 装配文档
- ai/govern/Untrusted：用户答案 / 知识库 / 简历不可信边界包裹，防 prompt 注入伪造闭合标签

### 驾驭工程 Harness
- AiClient 装饰器链显式定义：Audit → Quota → CircuitBreaker → Retry → HTTP
- AUTH 永不重试；RATE/UPSTREAM/NET/EMPTY 指数退避；FORMAT 3 轮自修复（maxTokens×1.5 上限 8000）
- 配额：日调用/token 预算，超限 429 BUDGET 不打上游
- 熔断：连续 AUTH/UPSTREAM 失败 OPEN 60s，/api/ops/breaker/reset 可控复位

### 可观测性 Observability
- RequestIdFilter：X-Request-Id / MDC requestId 贯穿日志
- AccessLogInterceptor：方法/路径/状态/耗时
- AiAuditLog：JSONL 按日审计（scene/promptRef/model/attempt/outcome/tokens/latency/traceId/脱敏摘要）
- AiMetrics：首过率、修复轮数、结果分类、平均时延
- Actuator：/actuator/health /actuator/metrics

### 可控性 Controllability
- 密钥：${AI_API_KEY} 环境变量注入，yml 无明文回退；aiLocked 设置页只读
- /api/ops/health|metrics|prompts|breaker|quota：运行时状态、指标、Prompt 清单、熔断/配额复位
- 健康检查含 aiReady / breaker；全局异常契约 {error, code?} 与前端一致

### 持久化（数据库层）
- StateRepository：state.json v2 原子写（tmp+rename，损坏备份），与 Node 版共享
- data/audit/ai-audit-*.jsonl：AI 调用全量留痕（成本/漂移/评测样本来源）

### 测试
- 领域单测：SM-2 / JsonExtractor / Untrusted / ContextPlan（mvn test）
- 端到端：server-java/e2e_browser.py（Playwright 真浏览器，17 项全链路）

### 启动
```bash
cd server-java
mvn package
java -Dfile.encoding=UTF-8 -jar target/learning-loop-server-1.0.0.jar
# 浏览器打开 http://localhost:3002
```
