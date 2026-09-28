package com.closeloop.project;

import com.closeloop.common.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历结构化数据与项目面试切面（数据源：Node 版 resume.js，2026-09-17「知缘Flow 情绪社区平台」版）。
 * 集中一处维护，AI Prompt 与 /api/projects 响应都从这里取数，避免多处复制。
 */
public final class ResumeData {

    private ResumeData() {}

    public static final String RESUME_VERSION = "2026-09-17";

    public record SkillGroup(String group, List<String> tags) {}
    public record SelfEval(String k, String v) {}
    public record Module(int no, String title, String tag, String text, List<String> techs) {}
    public record Facet(String id, String name, String desc, String text) {}

    public static final String NAME = "何宏鑫";
    public static final String TARGET = "Java后端开发实习生";
    // 公开仓库脱敏：联系方式不入库，本地运行时通过 application-local.yml 或环境变量补全
    public static final String PHONE = "";
    public static final String EMAIL = "";
    public static final String BLOG = "https://person-he.github.io";
    public static final String GITHUB = "https://github.com/Person-HE";

    public static final Map<String, Object> EDUCATION = Utils.m(
            "school", "中北大学", "major", "软件工程", "degree", "本科", "period", "2023.09 – 2027.06");

    public static final List<SkillGroup> SKILLS = List.of(
            new SkillGroup("后端框架", List.of("Spring Boot 3.4", "Spring MVC", "MyBatis-Plus", "Spring AOP", "RESTful 接口设计")),
            new SkillGroup("AI 工程", List.of("Langchain4j", "LLM 可靠性治理(熔断/重试/降级/结构化输出/超时隔离)", "ONNX Embedding", "RAG", "SSE 流式响应")),
            new SkillGroup("数据缓存", List.of("MySQL 8.0(批量/关联查询优化/联合索引/分页/事务)", "Redis(缓存/分布式锁/限流)", "Caffeine", "多级缓存一致性设计")),
            new SkillGroup("并发编程", List.of("线程池 ThreadPoolTaskExecutor", "@Async 异步化", "CompletableFuture", "ConcurrentHashMap", "ThreadLocal 隔离与清理")),
            new SkillGroup("中间件", List.of("RabbitMQ(事件驱动/死信队列/多消费者)", "WebSocket(鉴权握手/Session管理/心跳保活)")),
            new SkillGroup("工程工具", List.of("JMeter 压测", "Docker Compose")));

    public static final List<SelfEval> SELF_EVAL = List.of(
            new SelfEval("独立交付", "在校独立开发并可部署上线的情绪社区后端：31 张表建模、92 个 REST 接口（20 个 Controller），JMeter 控制变量压测验证缓存/降级/索引等关键路径。"),
            new SelfEval("工程能力", "多级缓存、事件驱动与故障降级、LLM 可靠性治理、SQL 与索引优化、AOP 横切与安全五大亮点，全部有可复现压测数据支撑（P99 274ms→6ms、EXPLAIN 397,293→50 等）。"),
            new SelfEval("AI 工程实践", "Langchain4j + LLM 可靠性治理（熔断/重试/降级/结构化输出校验/超时隔离），了解 ONNX Embedding、RAG、SSE 流式响应。"));

    public static final String P_NAME = "知缘Flow情绪社区平台";
    public static final String P_ROLE = "在校独立开发 · 可部署上线";
    public static final String P_PERIOD = "2025.11 – 至今";
    public static final String P_STACK = "Spring Boot 3.4 · MyBatis-Plus · MySQL 8.0 · Redis · Caffeine · RabbitMQ · WebSocket · Langchain4j · ONNX Embedding";
    public static final String P_DESC = "覆盖认证、圈子/帖子/评论、实时私信、内容审核与 AI 陪伴的情绪社区后端；31 张表建模、92 个 REST 接口（20 个 Controller），JMeter 控制变量压测验证缓存/降级/索引等关键路径。";

    public static final Map<String, Object> METRICS = Utils.m(
            "apiScale", "92 个 REST 接口 / 31 张表 / 20 个 Controller",
            "p99_100", "274ms→6ms",
            "p99_200", "284ms→19ms",
            "dbMiss", "6267→3 次/轮",
            "scanRows", "397,293→50",
            "mqDegrade", "50/50 成功 @ 40ms",
            "llmFallback", "1.6s→8ms / 降级率100%");

    public static final List<Module> MODULES = List.of(
            new Module(1, "多级缓存架构", "Caffeine L1 → Redis L2 → MySQL L3 读穿",
                    "针对热点详情读把 MySQL 打满、尾延迟飙升的问题，设计 Caffeine L1 → Redis L2 → MySQL L3 读穿——选 Caffeine 作 L1 是为消除网络 IO，Redis 承担多实例共享；叠加空值占位防穿透、SETNX 防击穿、过期时间 ±20% 抖动防雪崩。同一 jar 以 cache.bypass 做控制变量 A/B（JMeter 恒定吞吐、响应体 id 断言、每档 3 轮中位）：帖子详情 100 RPS 下 P99 274ms→6ms，每轮 DB 回源 6267→3 次；200 RPS 下 P99 284ms→19ms。",
                    List.of("Caffeine", "Redis", "多级缓存", "缓存穿透/击穿/雪崩", "SETNX", "JMeter A/B 压测")),
            new Module(2, "事件驱动与故障降级", "Topic 交换机 + 死信队列 + Confirm/Return",
                    "解决发帖主流程被同步审核/通知拖长的问题，将 post.created / audit / notification 投递到 Topic 交换机并挂死信队列，Confirm / Return 感知投递失败；MQ 不可达时 catch 后降级 auditAsync 直连，主流程不阻塞。实测 MQ 不可达下发帖 50/50 成功、avg 40ms，日志可对账。",
                    List.of("RabbitMQ", "Topic 交换机", "死信队列", "Confirm/Return", "故障降级", "@Async")),
            new Module(3, "LLM 可靠性治理", "熔断 → 递增退避重试 → 静态降级",
                    "解决上游模型超时把业务线程拖死的问题，基于模板方法封装「熔断（阈值 5 次失败 → OPEN，60s 后 HALF_OPEN）→ 递增退避重试 → 静态降级话术」。错误 Key 压测：熔断前约 1.6s，OPEN 后 8ms，降级率 100%。",
                    List.of("Langchain4j", "熔断", "重试退避", "降级", "超时隔离", "模板方法")),
            new Module(4, "SQL 查询与索引优化", "",
                    "解决评论列表 N+1 与慢查询问题，重构为单次 selectList 后内存构建评论树，并按查询条件建立联合索引 (post_id, create_time)；压测评论列表接口，P50 25ms / P95 35ms，EXPLAIN 扫描行数从 397,293 降至 50。",
                    List.of("SQL优化", "N+1 消除", "内存构建树", "联合索引", "EXPLAIN")),
            new Module(5, "工程化横切与安全", "三切面 + JWT 双 Token + BCrypt + AES 脱敏",
                    "针对限流、权限、操作日志在各接口重复编写的问题，基于 AOP 实现 @RateLimit / @RequirePermission / @OperationLog 三切面统一横切，覆盖 15 个控制器，163 处注解收敛至 3 个切面；设计 JWT 双 Token + BCrypt 加密 + AES 字段脱敏，策略模式统一路由权限，ThreadLocal 隔离上下文，权限判断收敛至 22 处，越权类缺陷 0 例。",
                    List.of("AOP 切面", "@RateLimit/@RequirePermission/@OperationLog", "JWT 双Token", "BCrypt", "AES 脱敏", "策略模式", "ThreadLocal")));

    /** 简历完整结构（/api/projects 响应用的 resume 字段，保持与 Node 版 JSON 形状一致） */
    public static Map<String, Object> resumeMap(String sourcePath) {
        List<Map<String, Object>> skills = new ArrayList<>();
        for (SkillGroup s : SKILLS) skills.add(Utils.m("group", s.group(), "tags", s.tags()));
        List<Map<String, Object>> selfEval = new ArrayList<>();
        for (SelfEval s : SELF_EVAL) selfEval.add(Utils.m("k", s.k(), "v", s.v()));
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Module m : MODULES) modules.add(Utils.m("no", m.no(), "title", m.title(), "tag", m.tag(), "text", m.text(), "techs", m.techs()));
        Map<String, Object> project = Utils.m(
                "name", P_NAME, "role", P_ROLE, "period", P_PERIOD, "stack", P_STACK, "desc", P_DESC,
                "metrics", METRICS, "modules", modules);
        return Utils.m(
                "name", NAME, "target", TARGET, "phone", PHONE, "email", EMAIL,
                "blog", BLOG, "github", GITHUB, "education", EDUCATION,
                "skills", skills, "selfEval", selfEval, "project", project,
                "sourcePath", sourcePath);
    }

    /** 面试切面：f-all（项目整体）+ 5 个亮点模块 */
    public static List<Facet> facets() {
        List<Facet> list = new ArrayList<>();
        String modulesLine = String.join(" / ", MODULES.stream().map(m -> m.no() + "." + m.title()).toList());
        list.add(new Facet("f-all", "项目整体（宏观架构与数据流）",
                "系统定位、模块划分、请求全链路（Nginx→Controller→Service→缓存→DB→MQ/WebSocket）、角色与独立完成度、总体技术选型。",
                "项目名称：" + P_NAME + "（" + P_ROLE + "，" + P_PERIOD + "）\n"
                        + "项目描述：" + P_DESC + "\n"
                        + "技术栈：" + P_STACK + "\n"
                        + "核心规模：" + METRICS.get("apiScale") + "\n"
                        + "关键压测指标：100RPS 下 P99 " + METRICS.get("p99_100") + "，200RPS 下 P99 " + METRICS.get("p99_200")
                        + "；每轮 DB 回源 " + METRICS.get("dbMiss") + "；评论列表 EXPLAIN 扫描行数 " + METRICS.get("scanRows")
                        + "；MQ 不可达降级 " + METRICS.get("mqDegrade") + "；LLM 熔断 " + METRICS.get("llmFallback") + "\n"
                        + "五个亮点模块：" + modulesLine));
        for (Module m : MODULES) {
            String desc = (m.tag().isEmpty() ? "" : m.tag() + "；") + Utils.cut(m.text(), 120) + "…";
            String text = "【" + m.title() + "】" + m.text() + "\n涉及技术：" + String.join("、", m.techs());
            list.add(new Facet("f" + m.no(), m.title(), desc, text));
        }
        return list;
    }

    public static Facet facetById(String id) {
        List<Facet> fs = facets();
        return fs.stream().filter(f -> f.id().equals(id)).findFirst().orElse(fs.get(0));
    }

    /** 简历全文（注入 Prompt 的候选人简历文本） */
    public static String buildResumeText() {
        StringBuilder modules = new StringBuilder();
        for (Module m : MODULES) {
            if (modules.length() > 0) modules.append('\n');
            modules.append(m.no()).append(". ").append(m.title()).append("：").append(m.text());
        }
        StringBuilder skills = new StringBuilder();
        for (SkillGroup s : SKILLS) {
            if (skills.length() > 0) skills.append("；");
            skills.append(s.group()).append("：").append(String.join("/", s.tags()));
        }
        StringBuilder self = new StringBuilder();
        for (SelfEval s : SELF_EVAL) {
            if (self.length() > 0) self.append("；");
            self.append(s.k()).append("：").append(s.v());
        }
        return "姓名：" + NAME + "｜目标：" + TARGET + "\n"
                + "教育：" + EDUCATION.get("school") + " " + EDUCATION.get("major") + " " + EDUCATION.get("degree") + "（" + EDUCATION.get("period") + "）\n"
                + "技术栈：" + skills + "\n"
                + "自我评价：" + self + "\n"
                + "项目：" + P_NAME + "（" + P_ROLE + "，" + P_PERIOD + "）\n"
                + "项目描述：" + P_DESC + "\n"
                + "项目技术栈：" + P_STACK + "\n"
                + "项目指标：" + METRICS.get("apiScale") + "；100RPS P99 " + METRICS.get("p99_100")
                + "；200RPS P99 " + METRICS.get("p99_200") + "；DB 回源 " + METRICS.get("dbMiss")
                + "；扫描行数 " + METRICS.get("scanRows") + "；MQ 降级 " + METRICS.get("mqDegrade")
                + "；LLM 熔断 " + METRICS.get("llmFallback") + "\n"
                + "项目亮点：\n" + modules;
    }
}
