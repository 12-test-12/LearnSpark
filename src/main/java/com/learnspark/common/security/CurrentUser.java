package com.learnspark.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前登录用户参数注解。
 *
 * <p>用于 Controller 方法参数，由 {@link CurrentUserArgumentResolver} 自动注入当前登录用户 ID。
 * 若请求未认证，则参数为 null（仅当接口本身不强制认证时使用）。
 *
 * <pre>
 * &#64;GetMapping("/me")
 * public ApiResult&lt;UserInfo&gt; me(&#64;CurrentUser String userId) {
 *     // userId 由 JWT 过滤器解析后注入
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /**
     * 是否必须登录。默认 true，未登录时抛 401。
     * 设为 false 时，未登录请求该参数为 null（用于可选登录的公开接口）。
     */
    boolean required() default true;
}
