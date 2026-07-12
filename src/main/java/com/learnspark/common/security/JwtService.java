package com.learnspark.common.security;

import com.learnspark.common.config.JwtProperties;
import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌服务：签发与校验。
 *
 * <p>使用 JJWT 0.12.5 的 HS256 算法，密钥来自 {@link JwtProperties#getSecret()}。
 * Token 载荷中仅存放 userId（subject），不存放敏感信息。
 *
 * <p>SSOT：项目内所有 JWT 的生成与解析都走本类，避免散落多处。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    /** 签名密钥，初始化时由 secret 字符串构建 */
    private SecretKey secretKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足 32 字节，无法用于 HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 JWT。
     *
     * @param userId 用户 ID（作为 subject 写入载荷）
     * @return compact JWT 字符串
     */
    public String generate(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 JWT，返回 subject（即 userId）。
     *
     * @param token compact JWT 字符串
     * @return userId
     * @throws BusinessException token 非法或已过期
     */
    public String parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 解析失败: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
