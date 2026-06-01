package com.start.agent.security;

import com.start.agent.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final AppSecurityProperties props;

    public JwtService(AppSecurityProperties props) {
        this.props = props;
    }

    public String createToken(String username, String role, Long userId) {
        long now = System.currentTimeMillis();
        Date exp = new Date(now + props.getJwtExpirationMs());
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(new Date(now))
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public Optional<Claims> parseValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 从有效 Claims 中提取用户 ID。 */
    public static Long getUserId(Claims claims) {
        Object uid = claims.get("userId");
        if (uid instanceof Number n) return n.longValue();
        if (uid instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** 从有效 Claims 中提取角色。 */
    public static String getRole(Claims claims) {
        Object r = claims.get("role");
        return r instanceof String s ? s : null;
    }

    private SecretKey signingKey() {
        byte[] bytes = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
