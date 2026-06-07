package com.labcourse.controller;

import com.labcourse.entity.Admin;
import com.labcourse.service.AdminService;
import com.labcourse.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        Admin admin = adminService.login(username, password);
        Map<String, Object> result = new HashMap<>();

        if (admin != null) {
            String token = jwtUtil.generateToken(admin.getId(), admin.getUsername(), "admin");
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("data", admin);
            result.put("token", token);
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return ResponseEntity.status(401).body(result);
        }
    }
}
