package com.labcourse.controller;

import com.labcourse.entity.Admin;
import com.labcourse.entity.Student;
import com.labcourse.entity.Teacher;
import com.labcourse.repository.AdminRepository;
import com.labcourse.repository.StudentRepository;
import com.labcourse.repository.TeacherRepository;
import com.labcourse.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AdminRepository adminRepository;

    /**
     * Token 刷新 — 使用 Refresh Token 轮转获取新令牌
     * Security fix (HIGH-001): 每次刷新生成新的 Access Token 和 Refresh Token
     * 旧的 Refresh Token 立即失效，防止 Token 重放攻击
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            response.put("success", false);
            response.put("message", "缺少refreshToken参数");
            return ResponseEntity.badRequest().body(response);
        }

        // 验证 Refresh Token
        Claims claims = jwtUtil.validateRefreshToken(refreshToken);
        if (claims == null) {
            response.put("success", false);
            response.put("message", "refreshToken无效或已过期");
            return ResponseEntity.status(401).body(response);
        }

        Long userId = Long.valueOf(claims.getSubject());

        // 查找用户并验证 refreshToken 是否匹配（防止重放攻击）
        Optional<?> userOpt = findUserByRefreshToken(refreshToken);
        if (userOpt.isEmpty()) {
            // Refresh Token 不匹配 — 可能是重放攻击或已轮转
            response.put("success", false);
            response.put("message", "refreshToken已被使用或无效");
            return ResponseEntity.status(401).body(response);
        }

        // 获取用户信息
        String username = null;
        String role = null;
        Object user = userOpt.get();
        if (user instanceof Student s) {
            username = s.getStudentNo();
            role = "student";
        } else if (user instanceof Teacher t) {
            username = t.getTeacherNo();
            role = "teacher";
        } else if (user instanceof Admin a) {
            username = a.getUsername();
            role = "admin";
        }

        // 生成新的 Access Token 和 Refresh Token（轮转）
        String newAccessToken = jwtUtil.generateAccessToken(userId, username, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        // 保存新的 Refresh Token 到数据库（覆盖旧的）
        saveRefreshToken(user, newRefreshToken);

        response.put("success", true);
        response.put("message", "Token刷新成功");
        response.put("accessToken", newAccessToken);
        response.put("refreshToken", newRefreshToken);
        return ResponseEntity.ok(response);
    }

    /**
     * 登出 — 清除 Refresh Token，使所有已签发 Token 失效
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            if (claims != null) {
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                // 清除数据库中的 refreshToken
                clearRefreshToken(userId, role);
            }
        }

        response.put("success", true);
        response.put("message", "登出成功");
        return ResponseEntity.ok(response);
    }

    // ========== 辅助方法 ==========

    private Optional<?> findUserByRefreshToken(String refreshToken) {
        Optional<Student> student = studentRepository.findByRefreshToken(refreshToken);
        if (student.isPresent()) return student;

        Optional<Teacher> teacher = teacherRepository.findByRefreshToken(refreshToken);
        if (teacher.isPresent()) return teacher;

        Optional<Admin> admin = adminRepository.findByRefreshToken(refreshToken);
        if (admin.isPresent()) return admin;

        return Optional.empty();
    }

    private void saveRefreshToken(Object user, String refreshToken) {
        if (user instanceof Student s) {
            s.setRefreshToken(refreshToken);
            studentRepository.save(s);
        } else if (user instanceof Teacher t) {
            t.setRefreshToken(refreshToken);
            teacherRepository.save(t);
        } else if (user instanceof Admin a) {
            a.setRefreshToken(refreshToken);
            adminRepository.save(a);
        }
    }

    private void clearRefreshToken(Long userId, String role) {
        if ("student".equals(role)) {
            studentRepository.findById(userId).ifPresent(s -> {
                s.setRefreshToken(null);
                studentRepository.save(s);
            });
        } else if ("teacher".equals(role)) {
            teacherRepository.findById(userId).ifPresent(t -> {
                t.setRefreshToken(null);
                teacherRepository.save(t);
            });
        } else if ("admin".equals(role)) {
            adminRepository.findById(userId).ifPresent(a -> {
                a.setRefreshToken(null);
                adminRepository.save(a);
            });
        }
    }
}