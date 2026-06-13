package com.labcourse.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    // Security fix (HIGH-001): Access Token 30分钟，Refresh Token 7天
    private static final long ACCESS_EXPIRATION = 30 * 60 * 1000;      // 30分钟
    private static final long REFRESH_EXPIRATION = 7 * 24 * 60 * 60 * 1000; // 7天

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ========== Access Token ==========

    /**
     * 生成短期 Access Token（30分钟），用于 API 认证
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 验证 Access Token 并返回 Claims
     */
    public Claims validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("type"))) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== Refresh Token ==========

    /**
     * 生成长期 Refresh Token（7天），用于刷新 Access Token
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tokenId", UUID.randomUUID().toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 验证 Refresh Token 并返回 Claims
     */
    public Claims validateRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"refresh".equals(claims.get("type"))) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 通用方法 ==========

    /**
     * 验证 Token 是否有效（返回 boolean，供 JwtFilter 使用）
     */
    public boolean validateToken(String token) {
        return validateAccessToken(token) != null;
    }

    /**
     * 解析 Token 返回 Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return Long.valueOf(claims.getSubject());
        }
        return null;
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.get("username", String.class);
        }
        return null;
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.get("role", String.class);
        }
        return null;
    }

    // 别名方法用于JwtFilter
    public Long extractUserId(String token) {
        return getUserIdFromToken(token);
    }

    public String extractRole(String token) {
        return getRoleFromToken(token);
    }

    public boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.getExpiration().before(new Date());
        }
        return true;
    }
}