package com.labcourse.controller;

import com.labcourse.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                result.put("success", false);
                result.put("message", "无效的Token格式");
                return ResponseEntity.status(401).body(result);
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                result.put("success", false);
                result.put("message", "Token已过期，请重新登录");
                return ResponseEntity.status(401).body(result);
            }

            Long userId = jwtUtil.extractUserId(token);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);

            String newToken = jwtUtil.generateToken(userId, username, role);
            long remainingTime = jwtUtil.getRemainingTime(newToken);

            result.put("success", true);
            result.put("message", "Token刷新成功");
            result.put("token", newToken);
            result.put("expiresIn", remainingTime);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Token刷新失败");
            return ResponseEntity.status(401).body(result);
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                result.put("success", false);
                result.put("valid", false);
                return ResponseEntity.ok(result);
            }

            String token = authHeader.substring(7);

            if (jwtUtil.validateToken(token)) {
                long remainingTime = jwtUtil.getRemainingTime(token);
                result.put("success", true);
                result.put("valid", true);
                result.put("expiresIn", remainingTime);
                result.put("userId", jwtUtil.extractUserId(token));
                result.put("role", jwtUtil.extractRole(token));
            } else {
                result.put("success", true);
                result.put("valid", false);
                result.put("message", "Token已过期");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("valid", false);
            return ResponseEntity.ok(result);
        }
    }
}
