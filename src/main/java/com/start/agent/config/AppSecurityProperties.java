package com.start.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可选启用：管理员 JWT 登录 + 小说「仅管理员可见」书库标签。
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /** false 时不校验 JWT，书库不做隐藏（兼容旧部署）。 */
    private boolean enabled = false;

    private String adminUsername = "admin";

    /** 建议使用环境变量注入，勿提交明文密码。 */
    private String adminPassword = "changeme";

    /**
     * HS256 密钥，建议 ≥32 字节随机串；可用环境变量 JWT_SECRET。
     */
    private String jwtSecret = "novel-agent-dev-secret-change-me-32bytes-minimum!!!";

    /** Access Token 有效期（毫秒），默认 24h。 */
    private long jwtExpirationMs = 86_400_000L;
}
