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

    private String testToken;

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
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist()) // 密码不应返回
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 保存Token用于后续测试
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        testToken = (String) result.get("token");
        assertNotNull(testToken);
        System.out.println("Login success, token: " + testToken.substring(0, 50) + "...");
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
                .andExpect(jsonPath("$.token").exists())
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
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }
}
