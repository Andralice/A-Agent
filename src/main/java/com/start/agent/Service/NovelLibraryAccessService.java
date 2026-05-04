package com.start.agent.service;

import com.start.agent.config.AppSecurityProperties;
import com.start.agent.exception.NotFoundException;
import com.start.agent.model.Novel;
import com.start.agent.repository.NovelRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 书库可见性：{@link Novel#isLibraryPublic()} 为 false 时仅管理员 JWT 可读 HTTP 详情。
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
        if (!securityProperties.isEnabled()) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public List<Novel> listNovelsForCaller() {
        if (!securityProperties.isEnabled() || isAdmin()) {
            return novelRepository.findAll();
        }
        return novelRepository.findByLibraryPublicTrueOrderByCreateTimeDesc();
    }

    /**
     * 非公开且非管理员：抛出 {@link NotFoundException#novel()}（404，避免泄露存在性）。
     */
    public void assertCanRead(Long novelId) {
        if (!securityProperties.isEnabled()) {
            return;
        }
        Novel novel = novelRepository.findById(novelId).orElseThrow(NotFoundException::novel);
        if (novel.isLibraryPublic()) {
            return;
        }
        if (!isAdmin()) {
            throw NotFoundException.novel();
        }
    }

    public void assertAdminIfSecurityEnabled() {
        if (!securityProperties.isEnabled()) {
            return;
        }
        if (!isAdmin()) {
            throw new ForbiddenLibraryOperationException();
        }
    }

    /** 修改书库可见性等管理操作：启用安全时必须登录管理员。 */
    public static class ForbiddenLibraryOperationException extends RuntimeException {
        public ForbiddenLibraryOperationException() {
            super("需要管理员登录");
        }
    }
}
