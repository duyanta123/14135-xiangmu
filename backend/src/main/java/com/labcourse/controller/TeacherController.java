package com.labcourse.controller;

import com.labcourse.entity.Teacher;
import com.labcourse.service.TeacherService;
import com.labcourse.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String teacherNo = loginData.get("teacherNo");
        String password = loginData.get("password");

        Teacher teacher = teacherService.login(teacherNo, password);
        Map<String, Object> result = new HashMap<>();

        if (teacher != null) {
            String token = jwtUtil.generateToken(teacher.getId(), teacher.getTeacherNo(), "teacher");
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("data", teacher);
            result.put("token", token);
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "工号或密码错误");
            return ResponseEntity.status(401).body(result);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", teacherService.list());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Teacher teacher) {
        Map<String, Object> result = new HashMap<>();
        boolean success = teacherService.save(teacher);
        result.put("success", success);
        result.put("message", success ? "添加成功" : "添加失败");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> update(@RequestBody Teacher teacher) {
        Map<String, Object> result = new HashMap<>();
        boolean success = teacherService.updateById(teacher);
        result.put("success", success);
        result.put("message", success ? "更新成功" : "更新失败");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        boolean success = teacherService.removeById(id);
        result.put("success", success);
        result.put("message", success ? "删除成功" : "删除失败");
        return ResponseEntity.ok(result);
    }
}
