package com.start.agent.controller;

import com.start.agent.config.AppSecurityProperties;
import com.start.agent.model.User;
import com.start.agent.security.JwtService;
import com.start.agent.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AppSecurityProperties props;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthController(AppSecurityProperties props, JwtService jwtService, UserService userService) {
        this.props = props;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    /** 获取客户端 IP。 */
    private static String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return req.getRemoteAddr();
    }

    /** 注册普通用户。 */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest body, HttpServletRequest req) {
        if (!props.isEnabled()) {
            return badRequest("AUTH_DISABLED", "当前未启用登录");
        }
        if (body == null || body.getUsername() == null || body.getPassword() == null) {
            return badRequest("INVALID_ARGUMENT", "用户名和密码不能为空");
        }
        try {
            User user = userService.register(body.getUsername().trim(), body.getPassword());
            String ip = clientIp(req);
            Map<String, Object> ok = new HashMap<>();
            ok.put("status", "success");
            ok.put("code", "OK");
            ok.put("message", "注册成功");
            ok.put("userId", user.getId());
            ok.put("username", user.getUsername());
            ok.put("role", user.getRole());
            ok.put("ip", ip);
            return ResponseEntity.ok(ok);
        } catch (IllegalArgumentException e) {
            return badRequest("INVALID_ARGUMENT", e.getMessage());
        }
    }

    /** 登录（管理员和普通用户均可，走 DB 验证）。 */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body, HttpServletRequest req) {
        if (!props.isEnabled()) {
            return badRequest("AUTH_DISABLED", "当前未启用登录（app.security.enabled=false）");
        }
        if (body == null || body.getUsername() == null || body.getPassword() == null) {
            return unauthorized("用户名或密码不能为空");
        }
        String ip = clientIp(req);
        Optional<User> userOpt = userService.authenticate(body.getUsername().trim(), body.getPassword(), ip);
        if (userOpt.isEmpty()) {
            log.warn("登录失败：用户名或密码错误（用户={}, IP={}）", body.getUsername().trim(), ip);
            return unauthorized("用户名或密码错误");
        }
        User user = userOpt.get();
        String token = jwtService.createToken(user.getUsername(), user.getRole(), user.getId());
        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("code", "OK");
        ok.put("message", "登录成功");
        ok.put("token", token);
        ok.put("tokenType", "Bearer");
        ok.put("expiresInMs", props.getJwtExpirationMs());
        ok.put("userId", user.getId());
        ok.put("username", user.getUsername());
        ok.put("role", user.getRole());
        ok.put("ip", ip);
        ok.put("location", user.getLastLoginLocation());
        return ResponseEntity.ok(ok);
    }

    /** 管理员查看所有用户列表（含最后登录 IP 和时间）。 */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        if (!props.isEnabled()) {
            return ResponseEntity.ok(List.of());
        }
        // 仅管理员可查看
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error"); err.put("code", "FORBIDDEN"); err.put("message", "仅管理员可查看用户列表");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }
        var users = userService.findAll().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("lastLoginIp", u.getLastLoginIp());
            m.put("lastLoginLocation", u.getLastLoginLocation());
            m.put("lastLoginTime", u.getLastLoginTime() != null ? u.getLastLoginTime().toString() : null);
            m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(users);
    }

    /** 当前会话信息。 */
    @GetMapping("/me")
    public Map<String, Object> me() {
        Map<String, Object> m = new HashMap<>();
        if (!props.isEnabled()) {
            m.put("securityEnabled", false);
            m.put("authenticated", false);
            m.put("role", null);
            m.put("userId", null);
            m.put("username", null);
            return m;
        }
        m.put("securityEnabled", true);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authed = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
        m.put("authenticated", authed);
        if (authed && auth.getPrincipal() instanceof Claims claims) {
            m.put("username", claims.getSubject());
            m.put("role", JwtService.getRole(claims));
            m.put("userId", JwtService.getUserId(claims));
        } else {
            m.put("username", null);
            m.put("role", null);
            m.put("userId", null);
        }
        return m;
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String code, String message) {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "error"); res.put("code", code); res.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    private static ResponseEntity<Map<String, Object>> unauthorized(String message) {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "error"); res.put("code", "UNAUTHORIZED"); res.put("message", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }

    @Data
    static class RegisterRequest { private String username; private String password; }

    @Data
    static class LoginRequest { private String username; private String password; }
}
