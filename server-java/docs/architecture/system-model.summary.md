# server-java 系统模型 · 摘要

**范围**：`server-java/`（Spring Boot 3.3.5 / Java 17，端口 3002）当前态架构模型。Node 版 `server/` 与 `client/` 视为外部协作者，不建模其内部。

**一句话**：单进程、文件态、AI 驱动的个人学习闭环后端；REST 契约镜像 Node 版，数据文件双后端共享；分层单向依赖，AI 工程化收敛为唯一网关出口，状态写入收敛为唯一 StateManager。

**最重要的五个设计事实**
1. 四层单向依赖 web→service→learning→state + 叶子支撑层；实测 import 图仅 3 条受控例外（X1-X3，全部只读类型/注册类）。
2. 全部 AI 流量经 `AiClient.chatJson`：重试分类（AUTH 永不重试）→ JsonExtractor 三级解析 → validator 领域合同 → 3 轮自修复。
3. 全部状态突变经 `StateManager.mutate`（进程内互斥 + tmp/原子 rename 落盘）；AI 生成期持 withGenLock，冲突返回 400。
4. 学习理论集中在纯逻辑 `learning` 包（SM-2+FSRS、四级队列优先级、作答回写、KPI），不依赖 Spring 即可测。
5. 契约保真靠三招：Utils.m 键序、错误形状 `{"error","code?"}`、AI 结果 snake_case 白名单规范化。

**工件**
- `learning-loop.structurizr.dsl` — C1 上下文 / C2 容器 / C3 组件（Qoder Structurizr DSL 预览器可开）
- `system-dependencies.dot` — 包依赖实测图（边权=import 数，红色虚线=例外）
- `learning-loop-path.dot` — 端到端学习闭环主路径
- `system-model.evidence.md` — 节点/边证据与置信度索引
- `architecture-understanding.md` — 详细设计叙述（§1-§10）

**主要未知项**：其余 AI 端点逐个实跑（V1）、缺口 resolve 实机路径（V2）、kb refresh 增量（V3）、双后端并写约定（V4）。详见 understanding §10。

**维护提示**：新增包或出现第 4 条例外依赖时，重跑 grep 边权命令（见 evidence 头注）并同步 `.dot`/`.dsl`；两者是模型事实源，渲染产物为派生物。
