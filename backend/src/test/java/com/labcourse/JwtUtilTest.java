package com.labcourse;

import com.labcourse.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        // 通过反射注入 secret（避免依赖 Spring 上下文和 MySQL）
        setField(jwtUtil, "secret", "test-secret-key-for-unit-tests-must-be-at-least-256-bits-long!!");
        setField(jwtUtil, "expiration", 86400000L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ========== Access Token 测试 ==========

    @Test
    void testGenerateAccessToken() {
        String token = jwtUtil.generateAccessToken(1L, "testUser", "student");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        System.out.println("Generated Access Token: " + token);
    }

    @Test
    void testParseToken() {
        Long userId = 100L;
        String username = "testStudent";
        String role = "student";

        String token = jwtUtil.generateAccessToken(userId, username, role);
        Claims claims = jwtUtil.parseToken(token);

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(username, claims.get("username", String.class));
        assertEquals(role, claims.get("role", String.class));
        assertEquals("access", claims.get("type", String.class));
    }

    @Test
    void testGetUsernameFromToken() {
        String username = "testTeacher";
        String token = jwtUtil.generateAccessToken(2L, username, "teacher");
        assertEquals(username, jwtUtil.getUsernameFromToken(token));
    }

    @Test
    void testExtractUserId() {
        Long userId = 999L;
        String token = jwtUtil.generateAccessToken(userId, "admin", "admin");
        assertEquals(userId, jwtUtil.extractUserId(token));
    }

    @Test
    void testExtractRole() {
        String role = "teacher";
        String token = jwtUtil.generateAccessToken(5L, "t001", role);
        assertEquals(role, jwtUtil.extractRole(token));
    }

    @Test
    void testTokenNotExpired() {
        String token = jwtUtil.generateAccessToken(1L, "user", "student");
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    void testValidateToken() {
        String token = jwtUtil.generateAccessToken(1L, "valid", "student");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "this.is.not.a.valid.token.at.all";
        assertFalse(jwtUtil.validateToken(invalidToken));
    }

    @Test
    void testDifferentTokens() {
        // 不同 userId 产生不同 token
        String token1 = jwtUtil.generateAccessToken(1L, "user1", "student");
        String token2 = jwtUtil.generateAccessToken(2L, "user1", "student");
        assertNotEquals(token1, token2);
    }

    // ========== Refresh Token 测试 ==========

    @Test
    void testGenerateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(1L);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        System.out.println("Generated Refresh Token: " + token);
    }

    @Test
    void testValidateAccessToken() {
        String token = jwtUtil.generateAccessToken(1L, "user", "student");
        Claims claims = jwtUtil.validateAccessToken(token);
        assertNotNull(claims);
        assertEquals("1", claims.getSubject());
        assertEquals("access", claims.get("type", String.class));
    }

    @Test
    void testValidateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(1L);
        Claims claims = jwtUtil.validateRefreshToken(token);
        assertNotNull(claims);
        assertEquals("1", claims.getSubject());
        assertEquals("refresh", claims.get("type", String.class));
    }

    @Test
    void testAccessTokenRejectedAsRefreshToken() {
        String accessToken = jwtUtil.generateAccessToken(1L, "user", "student");
        Claims claims = jwtUtil.validateRefreshToken(accessToken);
        assertNull(claims, "Access Token 不应通过 Refresh Token 验证");
    }

    @Test
    void testRefreshTokenRejectedAsAccessToken() {
        String refreshToken = jwtUtil.generateRefreshToken(1L);
        // validateToken 内部调用 validateAccessToken，Refresh Token 应被拒绝
        assertFalse(jwtUtil.validateToken(refreshToken),
                "Refresh Token 不应通过 Access Token 验证");
    }

    @Test
    void testGenerateAccessAndRefreshTokensAreDifferent() {
        String access = jwtUtil.generateAccessToken(1L, "user", "student");
        String refresh = jwtUtil.generateRefreshToken(1L);
        assertNotEquals(access, refresh, "Access Token 和 Refresh Token 应不同");
    }
}