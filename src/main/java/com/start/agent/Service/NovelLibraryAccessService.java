package com.start.agent.service;

import com.start.agent.config.AppSecurityProperties;
import com.start.agent.exception.NotFoundException;
import com.start.agent.model.Novel;
import com.start.agent.repository.NovelRepository;
import com.start.agent.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 书库可见性：管理员看全部，普通用户看自己的+其他人公开的，访客只看公开的。
 */
@Service
public class NovelLibraryAccessService {

    private final AppSecurityProperties securityProperties;
    private final NovelRepository novelRepository;

    public NovelLibraryAccessService(AppSecurityProperties securityProperties, NovelRepository novelRepository) {
        this.securityProperties = securityProperties;
        this.novelRepository = novelRepository;
    }

    /** 关闭安全开关时视为管理员（全量可见）。 */
    public boolean isAdmin() {
        if (!securityProperties.isEnabled()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
    }

    /** 是否已登录（管理员或普通用户）。 */
    public boolean isAuthenticated() {
        if (!securityProperties.isEnabled()) return false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    /** 当前用户 ID（从 JWT Claims 提取）。 */
    public Long getCurrentUserId() {
        if (!securityProperties.isEnabled()) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Claims claims) {
            return JwtService.getUserId(claims);
        }
        return null;
    }

    /** 当前用户名。 */
    public String getCurrentUsername() {
        if (!securityProperties.isEnabled()) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Claims claims) {
            return claims.getSubject();
        }
        return null;
    }

    /**
     * 书列表：
     * - 管理员：全部
     * - 普通用户：自己的小说（不限可见性）+ 其他人公开的
     * - 访客：仅公开的
     */
    public List<Novel> listNovelsForCaller() {
        if (!securityProperties.isEnabled() || isAdmin()) {
            return novelRepository.findAll();
        }
        Long uid = getCurrentUserId();
        if (uid != null) {
            return novelRepository.findByUserIdOrLibraryPublicTrueOrderByCreateTimeDesc(uid);
        }
        return novelRepository.findByLibraryPublicTrueOrderByCreateTimeDesc();
    }

    /**
     * 非公开且非本人且非管理员：404（避免泄露存在性）。
     * 普通用户可以看到自己的非公开小说。
     */
    public void assertCanRead(Long novelId) {
        if (!securityProperties.isEnabled()) return;
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        if (novel.isLibraryPublic()) return;
        if (isAdmin()) return;
        Long uid = getCurrentUserId();
        if (uid != null && uid.equals(novel.getUserId())) return;
        throw NotFoundException.novel();
    }

    /** 删除操作：管理员或所有者可删。 */
    public void assertCanDelete(Long novelId) {
        if (!securityProperties.isEnabled()) return;
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        if (isAdmin()) return;
        Long uid = getCurrentUserId();
        if (uid != null && uid.equals(novel.getUserId())) return;
        throw new ForbiddenLibraryOperationException("只能删除自己的小说");
    }

    public void assertAdminIfSecurityEnabled() {
        if (!securityProperties.isEnabled()) return;
        if (!isAdmin()) throw new ForbiddenLibraryOperationException();
    }

    public static class ForbiddenLibraryOperationException extends RuntimeException {
        public ForbiddenLibraryOperationException() { super("需要管理员登录"); }
        public ForbiddenLibraryOperationException(String msg) { super(msg); }
    }
}
