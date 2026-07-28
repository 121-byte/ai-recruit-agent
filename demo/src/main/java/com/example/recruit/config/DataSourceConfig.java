package com.example.recruit.config;

import org.springframework.context.annotation.Configuration;

/**
 * 数据源配置 (P0 对齐: 删除 H2 降级)。
 *
 * <p>原复刻为兼容无 PostgreSQL 环境引入了 H2 {@code @ConditionalOnProperty(app.mock.enabled)}
 * 降级数据源, 偏离原项目。现已接入真实远程 PostgreSQL+pgvector, 删除 H2 降级,
 * 数据源固定由 {@code spring.datasource.*} 指定的 PostgreSQL 自动配置。
 *
 * <p>本类保留为扩展点 (如未来需要多数据源/Hikari 细调), 当前不注册任何 Bean。
 */
@Configuration
public class DataSourceConfig {
    // 无 H2 降级 Bean; 数据源由 DataSourceAutoConfiguration 按 spring.datasource.* 创建。
}
