package com.start.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

/** 数据源 Bean：可带连接失败重试，便于容器/MySQL 晚于应用就绪时的启动场景。 */
@Slf4j
@Configuration
public class DatabaseRetryConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = createDataSource();

        // 不在启动阶段强制阻塞等待数据库，避免数据库短暂不可用时应用无法启动
        // 连接池会在真正访问数据库时按需建立连接。
        try {
            log.info("【数据库连接】已初始化数据源，jdbcUrl={}", simplifyJdbcUrl(jdbcUrl));
            log.info("【数据库连接】首次连接将在实际使用时建立");
            return dataSource;
        } catch (Exception e) {
            log.error("【数据库连接】数据源初始化失败", e);
            throw new IllegalStateException("数据库数据源初始化失败，请检查 MySQL 服务或连接配置", e);
        }
    }

    private HikariDataSource createDataSource() {
        validateJdbcUrlSafety(jdbcUrl);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // 连接池配置
        dataSource.setMinimumIdle(5);
        dataSource.setMaximumPoolSize(20);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setConnectionTimeout(30000);
        dataSource.setInitializationFailTimeout(-1);
        dataSource.setValidationTimeout(5000);
        dataSource.setConnectionTestQuery("SELECT 1");
        
        // MySQL 特定属性
        dataSource.addDataSourceProperty("autoReconnect", "true");
        dataSource.addDataSourceProperty("failOverReadOnly", "false");
        dataSource.addDataSourceProperty("maxReconnects", "10");
        dataSource.addDataSourceProperty("initialTimeout", "2");
        dataSource.addDataSourceProperty("reconnectAttempts", "5");
        dataSource.addDataSourceProperty("reconnectDelay", "3000");
        
        return dataSource;
    }

    private void validateJdbcUrlSafety(String url) {
        if (url == null) return;
        if (!url.contains(":3307")) return;
        String osName = System.getProperty("os.name", "").toLowerCase();
        // 3307 is reserved for local SSH tunnel usage.
        if (!osName.contains("windows")) {
            throw new IllegalStateException("检测到 3307 数据库端口，仅允许本地 Windows SSH 隧穿使用；当前环境已阻止启动。");
        }
    }

    private String simplifyJdbcUrl(String url) {
        if (url == null || url.isBlank()) return "N/A";
        int queryIdx = url.indexOf('?');
        return queryIdx > 0 ? url.substring(0, queryIdx) : url;
    }
}
