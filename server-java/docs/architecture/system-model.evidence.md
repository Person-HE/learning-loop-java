# server-java 系统模型 · 证据索引

> 配套：`architecture-understanding.md`、`learning-loop.structurizr.dsl`、`system-dependencies.dot`、`learning-loop-path.dot`
> 路径基准：`server-java/`（源码 = `src/main/java/com/closeloop/`）。置信度定义见插件 architecture-contract。
> 运行时证据：2026-09-19 本机 3002 实例 curl 实测（服务当前仍在后台运行，日志 /tmp/java-server.log）。

## 节点证据

| 节点 ID | 类型 | 置信 | sourceRefs / 证据 |
|---|---|---|---|
| actor.learner | actor | high | 单机自用无鉴权代码；`config/WebConfig.java`（CORS 全放开）为旁证 |
| sys.reactClient | external-system | high | `../client/`（未改动）；`config/WebConfig.java:15-21` CORS 允许 5173 |
| sys.aiApi | external-system | high | `ai/AiClient.java`（/chat/completions 调用）；`application.yml` app.ai.base-url/model；运行时：kp/points 实测返回真实 AI 结果 |
| sys.kbTree | external-system | high | `application.yml` app.kb-root=`../后端知识库`；`knowledge/KbService.java:137-150` 域扫描；运行时：/api/kb、/api/kb/doc 实测 |
| sys.nodeLegacy | external-system | high | `../server/`（只读共存，本次实现从未写入）；`application.yml` app.data-dir 指向 server/data |
| container.apiApp | container | high | `pom.xml`、`LearningLoopApplication.java`、`application.yml`（port 3002）；启动日志实测 |
| ds.stateJson | database | high | `state/StateRepository.java:39-68`（load/save/原子 rename/备份）；运行时：/api/state 返回真实进度（27 records、frozen:true） |
| ds.lcStore | database | high | `knowledge/LcDataService.java`；运行时：/api/lc 返回 101 题 |
| ds.resumeHtml | database | high | `application.yml` app.resume-html；`service/ProjectInterviewService.overview()`；运行时：/api/projects |
| ds.distDir | database | high | `config/StaticResourceConfig.java:32-38` |
| comp.web | component | high | `web/` 9 文件（8 Controller + RolloverInterceptor + Req） |
| comp.service | component | high | `service/` 11 文件 |
| comp.learning | component | high | `learning/` 5 文件；无任何 web/ai import（grep 实测） |
| comp.ai | component | high | `ai/` 6 文件 + `ai/prompt/` 7 文件 |
| comp.knowledge | component | high | `knowledge/` 4 文件 |
| comp.state | component | high | `state/` 2 + `state/model/` 8 文件 |
| comp.project | component | high | `project/ResumeData.java` |
| comp.infra | component | high | `config/` 4 + `common/` 4 文件 |

## 边证据（含协议/同步性）

| 边 | 类型/协议 | 置信 | sourceRefs |
|---|---|---|---|
| reactClient→apiApp | calls/HTTP sync | high | `web/*Controller.java` 全部 @RequestMapping("/api/...")；CORS `WebConfig.java` |
| apiApp→aiApi | calls/HTTP sync(带重试) | high | `ai/AiClient.java`（HttpClient、退避 500×2^n+抖动、AUTH 不重试）；运行时：/api/questions/gen 实测返回 4 题 |
| apiApp→kbTree | reads/文件 | high | `knowledge/KbService.java` walk/readString；`web/KbAssetController.java` + `StateViewService.resolveKbAsset`（扩展名白名单+防穿越） |
| apiApp→stateJson | reads+writes/文件 | high | `StateRepository.java:58-68`；`StateManager.mutate` 唯一调用面 |
| nodeLegacy→stateJson | reads+writes/文件 | **medium** | 由 app.data-dir 共享同一文件推断；跨进程并发行为未压测（→V4） |
| web→service | depends-on/in-process | high | import 计数 13（grep） |
| web→state | ensureToday 每请求 | high | `web/RolloverInterceptor.java`；注册于 `config/WebConfig.java` |
| service→{ai,state,common,knowledge,learning,project,config} | in-process | high | import 计数 38/45/27/10/8/4/7（grep 实测） |
| learning→state | 读写 model DTO | high | import 15；`learning/AnswerApplier.java`、`QueueBuilder.java` 签名均以 AppState 为入参 |
| X1: ai/prompt→state/project/knowledge | 仅类型引用 | high | `ai/prompt/KbPrompts.java`、`LcPrompts.java:4`、`ProjectPrompts.java:4`、`MockPrompts.java:4`、`ResumeContext.java:3`（import 行实测） |
| X2: state→knowledge | 启动合并 | high | `state/StateManager.java:4-6` |
| X3: config→web | 拦截器注册 | high | `config/WebConfig.java`（addInterceptors） |
| service→aiApi 的生成锁语义 | 进程内互斥 | high(代码)/未实测(并发) | `StateManager.withGenLock`、`GradingService`/`GapService` 调用点 |

## 行为证据（结构性 vs 行为性区分）

- **已实测行为**（运行时 curl）：health、state 读取、queue/build（同题复答项正确生成）、kb 列表、kb/doc 读文档、kp/points **真实 AI 生成+入库缓存**、questions/gen **真实 AI 出题+genSessions 写入**、lc 列表/详情、curve/summary、records、projects。
- **仅代码证据的行为**（→V1/V2 待实跑）：answer/score 回写 SM-2、restate、gaps test/resolve、weekly、lc explain/practice/ask、projects questions/score/mock/*、resume-score、settings/reset、rollover 跨天路径。
- **假设**：KB 域分类阈值（<3 篇=building）沿用 Node 语义，Node 侧未重跑对比（medium）。

## 未知/不做清单

- 无鉴权、无多用户、无 HTTPS、无水平扩展 —— 当前态设计边界，非缺陷。
- lc100.json 由 Node 侧维护写入，Java 只读；若未来 Java 侧要写，需先解决 V4。
