# 学习闭环系统 · Java 版（Spring Boot + React）

面向技术学习的**每日闭环学习系统**：知识库出题 → 作答 → AI 多维评分 → 标准答案对照 → 缺口入库 → SM-2 遗忘曲线调度复习，另含力扣刷题、AI 面试官项目深挖、RAG 知识检索与周报生成。

本仓库为 **Java 重构版**：后端 Spring Boot（`server-java/`），前端 React（`client/`），二者开箱即用；仓库内不含 Node 版后端、教学文档与任何密钥明文。

## 功能地图

| 模块 | 说明 |
|---|---|
| 今日闭环 | KPI + 到期队列，跨天自动滚动（补写 missedDays、冻结检查） |
| 答题工作台 | 先答后看、AI 评分 + 标准答案对照、缺口检测、卡壳标记 |
| 知识地图 | 知识库只读扫描：分类 × 域 × 章 × 知识点，YAML 头 + H2/H3 锚点 |
| 遗忘曲线 | SM-2 双因子调度、预测 R 曲线、实测跳升点 |
| 缺口库 | 六类缺口标签，复述答题达标自动消灭 |
| 力扣 | 题目/题解数据、AI 讲解、变式练习 |
| 项目面试 | AI 面试官基于简历切面深挖并评分，多轮模拟面试 |
| RAG | 知识切片入 Qdrant，本地嵌入（Ollama nomic-embed-text / sidecar），`/api/rag/search` |
| 周报 | AI 生成近 7 天学习周报 |

## 技术栈

- 后端：Java 17 · Spring Boot 3.3.5 · MyBatis · MySQL 8 · LangChain4j 0.36.2（OpenAI 兼容 + Ollama 嵌入 + Qdrant 向量库）· JDK HttpClient（零额外 HTTP 依赖）
- 前端：React 18 · Vite 5 · Monaco Editor
- AI 工程化：指数退避重试、JSON 结构化自修复（3 轮）、Prompt 注册表（版本化 scene）、上下文槽位预算装配、不可信输入防注入、日配额 + 熔断、JSONL 审计与指标、请求 ID 贯穿日志
- 存储：`STORAGE_MODE=mysql`（主路径 MySQL，`schema.sql` 建表）或 `json`（state.json 原子写 tmp+rename）

## 目录结构

```
├── server-java/                  Spring Boot 后端（端口 3002）
│   ├── src/main/java/com/closeloop/
│   │   ├── web/                  REST 控制器 + 过滤器/拦截器（日滚动、访问日志）
│   │   ├── service/              业务编排（出题/评分/缺口/曲线/周报/力扣/面试）
│   │   ├── learning/             纯算法层（SM-2、优先级、队列、KPI）
│   │   ├── ai/                   AI 工程化层：AiClient 装饰器链 + prompt/context/govern
│   │   ├── application/          RAG、MySQL 状态存储
│   │   ├── infrastructure/       LangChain4j 装配、持久化 entity/mapper、Qdrant 写入
│   │   ├── knowledge/ state/ config/ common/ observability/ project/
│   │   └── resources/            application.yml · schema.sql · mapper
│   ├── embedding-sidecar/        本地嵌入 sidecar（Node + @huggingface/transformers，768 维）
│   ├── docs/                     架构模型（C4/Structurizr/DOT）与企业级升级计划
│   ├── e2e_browser.py            Playwright 真浏览器端到端脚本
│   └── README.md                 后端详细设计文档
└── client/                       React 前端（dev 端口 5173，生产由后端托管 client/dist）
```

## 快速开始

### 1. 环境变量（仓库内无任何密钥明文）

| 变量 | 说明 |
|---|---|
| `AI_API_KEY` | 必填，OpenAI 兼容网关密钥；未配置时 AI 接口返回 401 提示 |
| `AI_BASE_URL` / `AI_MODEL` | 可选，默认 `https://api.agnes-ai.cn/v1` / `agnes-2.5-flash` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 必填，本地 MySQL 8 账号（先执行 `server-java/src/main/resources/schema.sql`） |
| `STORAGE_MODE` | `mysql`（默认）或 `json` |
| `QDRANT_HOST` / `QDRANT_GRPC_PORT` / `QDRANT_COLLECTION` | RAG 向量库，默认 `127.0.0.1 / 6334 / learning_chunks` |
| `APP_KB_ROOT` / `APP_DATA_DIR` / `APP_CLIENT_DIST` | 知识库根目录、数据目录、前端产物目录 |

### 2. 依赖服务

```bash
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.12.4   # RAG 向量库（可选）
ollama serve && ollama pull nomic-embed-text                     # 本地嵌入（可选，768 维）
```

### 3. 启动后端

```bash
cd server-java
mvn package -DskipTests
java -Dfile.encoding=UTF-8 -jar target/learning-loop-server-1.0.0.jar
# http://localhost:3002  健康检查 /api/health（含 aiReady / breaker 状态）
```

### 4. 启动前端

```bash
cd client
npm install
# 开发：vite 代理默认指向 3001，联调 Java 后端时改为 http://localhost:3002
npm run dev
# 生产：npm run build 后由后端直接托管 dist
```

## API 概览

| 域 | 端点 |
|---|---|
| 系统 | `GET /api/health` · `GET /api/state` · `GET /api/records` · `POST /api/settings` · `POST /api/state/reset` |
| 知识库 | `GET /api/kb` · `POST /api/kb/refresh` · `GET /api/kb/doc` |
| 队列与闭环 | `POST /api/queue/build` · `POST /api/questions/gen` · `POST /api/answer/score` · `POST /api/restate/score` · `GET /api/gaps` · `POST /api/gaps/{id}/resolve` |
| 曲线与周报 | `GET /api/curve` · `POST /api/weekly` |
| 力扣 | `GET /api/lc` · `POST /api/lc/{no}/explain` · `POST /api/lc/{no}/practice` |
| 项目面试 | `GET /api/projects` · `POST /api/projects/score` · `POST /api/projects/mock/start|turn|end` |
| RAG | `POST /api/rag/reindex` · `GET /api/rag/search` |
| 运维 | `/api/ops/health|metrics|prompts|quota` · `POST /api/ops/breaker/reset` |

## 说明

- 简历面试模块（`project/ResumeData.java`）的联系方式字段已置空，公开仓库不含个人手机号/邮箱；本地按需自行补全。
- 嵌入模型权重（`models/`）、Qdrant 数据、`node_modules/`、构建产物均不入库。
- 领域单测：`mvn test`（SM-2 / JsonExtractor / Untrusted / ContextPlan / QueueBuilder）。

## License

个人学习项目，暂未选择许可证。
