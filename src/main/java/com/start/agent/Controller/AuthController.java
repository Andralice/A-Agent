package com.start.agent.controller;

import com.start.agent.config.AppSecurityProperties;
import com.start.agent.security.JwtService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员登录（JWT）；可通过 {@code app.security.enabled=false} 关闭。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AppSecurityProperties props;
    private final JwtService jwtService;

    public AuthController(AppSecurityProperties props, JwtService jwtService) {
        this.props = props;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        if (!props.isEnabled()) {
            Map<String, Object> res = new HashMap<>();
            res.put("status", "error");
            res.put("code", "AUTH_DISABLED");
            res.put("message", "当前未启用登录（app.security.enabled=false）");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
        }
        if (body == null || body.getUsername() == null || body.getPassword() == null) {
            return unauthorized("用户名或密码不能为空");
        }
        String u = body.getUsername().trim();
        String p = body.getPassword();
        if (!constantEq(u, props.getAdminUsername()) || !constantEq(p, props.getAdminPassword())) {
            log.warn("登录失败：用户名或密码错误（用户={}）", u);
            return unauthorized("用户名或密码错误");
        }
        String token = jwtService.createToken(u);
        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("code", "OK");
        ok.put("message", "登录成功");
        ok.put("token", token);
        ok.put("tokenType", "Bearer");
        ok.put("expiresInMs", props.getJwtExpirationMs());
        ok.put("role", "ADMIN");
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Map<String, Object> m = new HashMap<>();
        if (!props.isEnabled()) {
            m.put("securityEnabled", false);
            m.put("authenticated", false);
            m.put("role", null);
            return m;
        }
        m.put("securityEnabled", true);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authed = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        m.put("authenticated", authed);
        m.put("role", authed ? "ADMIN" : null);
        return m;
    }

    private static ResponseEntity<Map<String, Object>> unauthorized(String message) {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "error");
        res.put("code", "UNAUTHORIZED");
        res.put("message", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }

    private static boolean constantEq(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        return MessageDigest.isEqual(x, y);
    }

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }
}
