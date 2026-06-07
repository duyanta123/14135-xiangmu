package com.labcourse;

import com.labcourse.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.jsonwebtoken.Claims;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void testGenerateToken() {
        String token = jwtUtil.generateToken(1L, "testUser", "student");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        System.out.println("Generated Token: " + token);
    }

    @Test
    void testExtractClaims() {
        Long userId = 100L;
        String username = "testStudent";
        String role = "student";

        String token = jwtUtil.generateToken(userId, username, role);
        Claims claims = jwtUtil.extractClaims(token);

        assertNotNull(claims);
        assertEquals(userId, claims.get("userId", Long.class));
        assertEquals(username, claims.getSubject());
        assertEquals(role, claims.get("role", String.class));
    }

    @Test
    void testExtractUsername() {
        String username = "testTeacher";
        String token = jwtUtil.generateToken(2L, username, "teacher");
        assertEquals(username, jwtUtil.extractUsername(token));
    }

    @Test
    void testExtractUserId() {
        Long userId = 999L;
        String token = jwtUtil.generateToken(userId, "admin", "admin");
        assertEquals(userId, jwtUtil.extractUserId(token));
    }

    @Test
    void testExtractRole() {
        String role = "teacher";
        String token = jwtUtil.generateToken(5L, "t001", role);
        assertEquals(role, jwtUtil.extractRole(token));
    }

    @Test
    void testTokenNotExpired() {
        String token = jwtUtil.generateToken(1L, "user", "student");
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    void testValidateToken() {
        String token = jwtUtil.generateToken(1L, "valid", "student");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "this.is.not.a.valid.token.at.all";
        assertFalse(jwtUtil.validateToken(invalidToken));
    }

    @Test
    void testDifferentTokens() {
        String token1 = jwtUtil.generateToken(1L, "user1", "student");
        String token2 = jwtUtil.generateToken(1L, "user1", "student");
        assertNotEquals(token1, token2); // 即使相同信息，每次生成的Token不同（有jti或时间戳差异）
    }
}
