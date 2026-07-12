package com.learnspark.common.security;

import com.learnspark.common.config.JwtProperties;
import com.learnspark.common.exception.BusinessException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>每个请求执行一次：从 Authorization 头提取 Bearer token，
 * 解析出 userId 后写入 {@link SecurityContextHolder}，供后续鉴权与 {@code @CurrentUser} 使用。
 *
 * <p>设计要点：
 * <ul>
 *   <li>无 token 或 token 非法时不抛异常，仅不设置认证上下文，交由 Security 鉴权规则处理</li>
 *   <li>principal 为 userId 字符串，{@link CurrentUserArgumentResolver} 据此注入</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                String userId = jwtService.parseUserId(token);
                setAuthentication(userId, request);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("JWT 校验失败，放行交由鉴权规则处理: {}", ex.getMessage());
                // 不在此处中断链，让 Security 鉴权规则决定是否 401
            } catch (BusinessException ex) {
                log.debug("JWT 业务异常，放行交由鉴权规则处理: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 从 Authorization 头剥离 Bearer 前缀 */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.getHeader());
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String prefix = jwtProperties.getPrefix();
        return header.startsWith(prefix) ? header.substring(prefix.length()) : null;
    }

    /** 将 userId 封装为 Authentication 写入 SecurityContext */
    private void setAuthentication(String userId, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
