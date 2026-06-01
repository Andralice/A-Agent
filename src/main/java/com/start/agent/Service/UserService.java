package com.start.agent.service;

import com.start.agent.config.AppSecurityProperties;
import com.start.agent.model.User;
import com.start.agent.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final IpLocationService ipLocationService;
    private final String adminUsername;
    private final String adminPassword;

    public UserService(UserRepository userRepository,
                       IpLocationService ipLocationService,
                       AppSecurityProperties props) {
        this.userRepository = userRepository;
        this.ipLocationService = ipLocationService;
        this.adminUsername = props.getAdminUsername();
        this.adminPassword = props.getAdminPassword();
    }

    /** 应用启动时确保管理员账号存在（密码来自 yml 配置，不存明文）。 */
    @PostConstruct
    public void ensureAdminUser() {
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = new User(adminUsername, hashPassword(adminPassword), "ADMIN");
            userRepository.save(admin);
            log.info("【用户系统】已创建管理员账号: {}", adminUsername);
        }
    }

    /** 注册普通用户。 */
    public User register(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 100) {
            throw new IllegalArgumentException("用户名不合法");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("密码至少4位");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User(username, hashPassword(password), "USER");
        user = userRepository.save(user);
        log.info("【用户系统】新用户注册: {}", username);
        return user;
    }

    /** 登录验证：先查 DB，若未启用安全则降级返回 null（不校验）。 */
    public Optional<User> authenticate(String username, String rawPassword, String ip) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return Optional.empty();
        User user = userOpt.get();
        if (!verifyPassword(rawPassword, user.getPasswordHash())) return Optional.empty();
        // 记录登录 IP、归属地、时间
        user.setLastLoginIp(ip);
        user.setLastLoginLocation(ipLocationService.resolve(ip));
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);
        log.info("【用户系统】{} 登录成功，IP: {}，归属地: {}", username, ip, user.getLastLoginLocation());
        return Optional.of(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    // ── 密码哈希 ──

    public static String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            md.update(salt);
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    public static boolean verifyPassword(String raw, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            byte[] salt = new byte[16];
            byte[] hash = new byte[combined.length - 16];
            System.arraycopy(combined, 0, salt, 0, 16);
            System.arraycopy(combined, 16, hash, 0, hash.length);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] testHash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(hash, testHash);
        } catch (Exception e) {
            return false;
        }
    }
}
