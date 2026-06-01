package com.start.agent.security;

import com.start.agent.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AppSecurityProperties props;
    private final JwtService jwtService;

    public JwtAuthenticationFilter(AppSecurityProperties props, JwtService jwtService) {
        this.props = props;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!props.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            jwtService.parseValid(token).ifPresent(this::setSecurityContext);
        }
        filterChain.doFilter(request, response);
    }

    private void setSecurityContext(Claims claims) {
        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            return;
        }
        String role = JwtService.getRole(claims);
        String authority = "ADMIN".equals(role) ? "ROLE_ADMIN" : "ROLE_USER";
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                claims,  // 存 Claims 以便后续提取 userId/role
                null,
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
