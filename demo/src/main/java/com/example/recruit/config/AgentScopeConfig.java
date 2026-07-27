package com.example.recruit.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AgentScope 配置 (复刻自文档 §13.2 + §二 config/AgentScopeConfig)。
 *
 * <p>初始化本地工作区目录 (agentscope.workspace.path=./workspace)。
 * AgentScope 运行时配置由 agentscope.properties 与各 Service 内的显式 Model 构造负责。
 */
@Configuration
public class AgentScopeConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeConfig.class);

    @PostConstruct
    void initWorkspace() {
        try {
            Path ws = Path.of("./workspace");
            Files.createDirectories(ws);
            Path data = Path.of("./data");
            Files.createDirectories(data);
            log.info("AgentScope workspace ready: {}", ws.toAbsolutePath());
        } catch (Exception e) {
            log.warn("init workspace failed: {}", e.getMessage());
        }
    }
}
