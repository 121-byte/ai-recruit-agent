package com.example.recruit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 智能招聘系统启动类。
 *
 * <p>基于 Spring Boot 3.4.1 + AgentScope 2.0.0，提供招聘全流程 Agent 化能力。
 * 启用 {@link EnableScheduling} 以支持记忆巩固 / 遗忘等定时任务。
 */
@SpringBootApplication
@MapperScan("com.example.recruit.dal.mapper")
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
