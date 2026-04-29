package com.start.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

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
            log.info("【数据库连接】已初始化数据源，首次连接将在实际使用时建立");
            return dataSource;
        } catch (Exception e) {
            log.error("【数据库连接】数据源初始化失败", e);
            throw new IllegalStateException("数据库数据源初始化失败，请检查 MySQL 服务或连接配置", e);
        }
    }

    private HikariDataSource createDataSource() {
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
}
