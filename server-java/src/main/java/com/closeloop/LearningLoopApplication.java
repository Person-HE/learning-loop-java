package com.closeloop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 面试知识闭环学习系统 · Spring Boot 后端入口。
 *
 * 模块分层（高内聚低耦合）：
 *  web        REST 控制器（唯一对外契约层，镜像 /api 全部端点）
 *  service    业务编排（出题/评分/闭环/力扣/项目/周报）
 *  learning   学习机制内核（SM-2 调度、队列组装、作答回写、KPI）——纯领域逻辑，不依赖 web/ai
 *  state      状态持久化（state.json v2 兼容）
 *  ai         AI 工程化（网关、重试容错、JSON 提取、Prompt 模板库）
 *  knowledge  知识库只读层（扫描/解析/力扣题库）
 *  project    简历与项目面试数据
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTransactionManagement
@MapperScan("com.closeloop.infrastructure.persistence.mapper")
public class LearningLoopApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningLoopApplication.class, args);
    }
}
