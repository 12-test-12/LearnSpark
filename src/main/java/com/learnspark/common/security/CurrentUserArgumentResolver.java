package com.learnspark.common.security;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} 参数解析器。
 *
 * <p>从 {@link SecurityContextHolder} 中取出当前认证主体（即 userId 字符串）注入到 Controller 参数。
 * JwtAuthenticationFilter 在解析 token 后会把 userId 设为 principal。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && String.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation == null || annotation.required();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            if (required) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return null;
        }

        // principal 由 JwtAuthenticationFilter 设置为 userId 字符串
        Object principal = auth.getPrincipal();
        if (principal instanceof String userId) {
            return userId;
        }
        if (required) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return null;
    }
}
