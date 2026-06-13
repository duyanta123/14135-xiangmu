package com.labcourse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcourse.entity.Student;
import com.labcourse.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings({"null", "unchecked"})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String testAccessToken;
    private String testRefreshToken;

    @BeforeEach
    void setUp() {
        // 确保数据库有测试用户
        Optional<Student> studentOpt = studentRepository.findByStudentNo("S001");
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            // 验证密码是否已加密
            if (!student.getPassword().startsWith("$2a$")) {
                student.setPassword(passwordEncoder.encode(student.getPassword()));
                studentRepository.save(student);
            }
        }
    }

    @Test
    void testStudentLoginSuccess() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("studentNo", "S001");
        loginRequest.put("password", "123456");

        String response = mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist()) // 密码不应返回
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 保存Token用于后续测试
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        testAccessToken = (String) result.get("accessToken");
        testRefreshToken = (String) result.get("refreshToken");
        assertNotNull(testAccessToken);
        assertNotNull(testRefreshToken);
        System.out.println("Login success, accessToken: " + testAccessToken.substring(0, 50) + "...");
    }

    @Test
    void testStudentLoginWrongPassword() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("studentNo", "S001");
        loginRequest.put("password", "wrongpassword");

        mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testStudentLoginNonExistentUser() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("studentNo", "NOT_EXIST");
        loginRequest.put("password", "123456");

        mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testAccessProtectedEndpointWithoutToken() throws Exception {
        // 没有Token访问受保护接口
        mockMvc.perform(get("/api/student/list"))
                .andExpect(status().isForbidden()); // 403
    }

    @Test
    void testAccessProtectedEndpointWithInvalidToken() throws Exception {
        String invalidToken = "Bearer invalid-token-123456";

        mockMvc.perform(get("/api/student/list")
                        .header("Authorization", invalidToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPasswordNotReturnedInResponse() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("studentNo", "S001");
        loginRequest.put("password", "123456");

        String response = mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 验证响应中不包含password
        assertFalse(response.contains("\"password\""));
    }

    @Test
    void testTeacherLoginSuccess() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("teacherNo", "T001");
        loginRequest.put("password", "123456");

        mockMvc.perform(post("/api/teacher/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void testAdminLoginSuccess() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "admin");
        loginRequest.put("password", "123456");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ========== HIGH-001: Token 轮转安全测试 ==========

    @Test
    void testTokenRotationFullFlow() throws Exception {
        // Step 1: 登录获取 accessToken 和 refreshToken
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("studentNo", "S001");
        loginRequest.put("password", "123456");

        String loginResponse = mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> loginResult = objectMapper.readValue(loginResponse, Map.class);
        String accessToken = (String) loginResult.get("accessToken");
        String refreshToken = (String) loginResult.get("refreshToken");
        assertNotNull(accessToken, "accessToken不应为空");
        assertNotNull(refreshToken, "refreshToken不应为空");

        // Step 2: 使用 refreshToken 刷新获取新令牌
        Map<String, String> refreshRequest = new HashMap<>();
        refreshRequest.put("refreshToken", refreshToken);

        String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> refreshResult = objectMapper.readValue(refreshResponse, Map.class);
        String newAccessToken = (String) refreshResult.get("accessToken");
        String newRefreshToken = (String) refreshResult.get("refreshToken");
        assertNotNull(newAccessToken, "新accessToken不应为空");
        assertNotNull(newRefreshToken, "新refreshToken不应为空");
        assertNotEquals(refreshToken, newRefreshToken, "刷新后refreshToken应轮转");

        // Step 3: 旧 refreshToken 已被轮转，再次使用应被拒绝
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))) // 使用旧的refreshToken
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRefreshWithAccessTokenFails() throws Exception {
        // 先登录
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("studentNo", "S001");
        loginRequest.put("password", "123456");

        String loginResponse = mockMvc.perform(post("/api/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> loginResult = objectMapper.readValue(loginResponse, Map.class);
        String accessToken = (String) loginResult.get("accessToken");

        // 尝试用 accessToken 去刷新（应该被拒绝，只能用 refreshToken）
        Map<String, String> badRefreshRequest = new HashMap<>();
        badRefreshRequest.put("refreshToken", accessToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRefreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
