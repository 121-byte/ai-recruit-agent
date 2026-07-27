package com.example.recruit.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置 —— Mock 降级 (复刻自文档 §13.1 + 启动降级需求)。
 *
 * <p>{@code app.mock.enabled=true} (默认) 或未配置 PG 密码时，使用嵌入式 H2 内存库，
 * 保证无 PostgreSQL 也能启动。配置真实 PG 凭据且关闭 mock 后切换到 PostgreSQL。
 *
 * <p>H2 模式下 pgvector 语法 ({@code <=>}, {@code VECTOR}, {@code ILIKE}) 在执行时报错，
 * 但记忆/向量相关服务均 try/catch 静默降级，不影响启动与主流程。
 */
@Configuration
public class DataSourceConfig {

    /**
     * Mock 模式：H2 内存库 (DBC h2:mem).
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.mock.enabled", havingValue = "true", matchIfMissing = true)
    public DataSource mockDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:airecruit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setPoolName("mock-h2-pool");
        return ds;
    }

    /**
     * 生产模式：PostgreSQL 数据源 (由 spring.datasource.* 自动配置)。
     * 此 Bean 仅在 app.mock.enabled=false 时生效，避免与默认自动配置冲突，
     * 故这里直接返回 null 交还给自动配置 —— 通过 @ConditionalOnProperty 不创建本 Bean 即可。
     */
    // 生产模式下不定义本方法，让 DataSourceAutoConfiguration 处理 spring.datasource.*。
}
