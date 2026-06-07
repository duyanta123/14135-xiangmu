package com.labcourse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PasswordEncoderTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testPasswordEncoderIsBCrypt() {
        assertNotNull(passwordEncoder);
        // BCryptPasswordEncoder 是默认实现
        assertTrue(passwordEncoder.getClass().getName().contains("BCrypt"));
    }

    @Test
    void testEncodePassword() {
        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertNotNull(encodedPassword);
        assertFalse(encodedPassword.isEmpty());
        assertTrue(encodedPassword.startsWith("$2a$") || 
                  encodedPassword.startsWith("$2b$") || 
                  encodedPassword.startsWith("$2y$"));
        
        System.out.println("Encoded password: " + encodedPassword);
        System.out.println("Length: " + encodedPassword.length());
    }

    @Test
    void testMatchesCorrectPassword() {
        String rawPassword = "test123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }

    @Test
    void testMatchesWrongPassword() {
        String encodedPassword = passwordEncoder.encode("correct123");
        
        assertFalse(passwordEncoder.matches("wrong123", encodedPassword));
    }

    @Test
    void testDifferentEncodingsForSamePassword() {
        String rawPassword = "samePassword";
        
        String encode1 = passwordEncoder.encode(rawPassword);
        String encode2 = passwordEncoder.encode(rawPassword);
        
        // 同一密码两次加密结果不同（带有不同的盐值）
        assertNotEquals(encode1, encode2);
        
        // 但都可以验证通过
        assertTrue(passwordEncoder.matches(rawPassword, encode1));
        assertTrue(passwordEncoder.matches(rawPassword, encode2));
    }

    @Test
    void testLongPasswordEncoding() {
        String longPassword = "this-is-a-very-long-password-for-testing-bcrypt-encoding-capabilities";
        String encoded = passwordEncoder.encode(longPassword);
        
        assertNotNull(encoded);
        assertTrue(passwordEncoder.matches(longPassword, encoded));
    }

    @Test
    void testSpecialCharactersPassword() {
        String specialPassword = "P@ssw0rd!#$%^&*()_+-=[]{}|;:,.<>?";
        String encoded = passwordEncoder.encode(specialPassword);
        
        assertNotNull(encoded);
        assertTrue(passwordEncoder.matches(specialPassword, encoded));
    }

    @Test
    void testEmptyPassword() {
        String emptyPassword = "";
        String encoded = passwordEncoder.encode(emptyPassword);
        
        assertNotNull(encoded);
        assertTrue(passwordEncoder.matches(emptyPassword, encoded));
    }

    @Test
    void testChinesePassword() {
        String chinesePassword = "中文密码123";
        String encoded = passwordEncoder.encode(chinesePassword);
        
        assertNotNull(encoded);
        assertTrue(passwordEncoder.matches(chinesePassword, encoded));
    }

    @Test
    void testPasswordConsistencyAcrossEncoders() {
        // 即使使用新的encoder，相同的密码和盐值应该匹配
        // 但这里我们验证的是同一个encoder的一致性
        String raw = "consistentPassword";
        String encoded = passwordEncoder.encode(raw);
        
        for (int i = 0; i < 5; i++) {
            assertTrue(passwordEncoder.matches(raw, encoded));
        }
    }
}
