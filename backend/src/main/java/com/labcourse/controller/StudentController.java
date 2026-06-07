package com.labcourse.controller;

import com.labcourse.entity.Student;
import com.labcourse.service.StudentService;
import com.labcourse.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String studentNo = loginData.get("studentNo");
        String password = loginData.get("password");

        Student student = studentService.login(studentNo, password);
        Map<String, Object> result = new HashMap<>();
        if (student != null) {
            String token = jwtUtil.generateToken(student.getId(), student.getStudentNo(), "student");
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("data", student);
            result.put("token", token);
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "学号或密码错误");
            return ResponseEntity.status(401).body(result);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", studentService.list());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Student student) {
        Map<String, Object> result = new HashMap<>();
        boolean success = studentService.save(student);
        result.put("success", success);
        result.put("message", success ? "添加成功" : "添加失败");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> update(@RequestBody Student student) {
        Map<String, Object> result = new HashMap<>();
        boolean success = studentService.updateById(student);
        result.put("success", success);
        result.put("message", success ? "更新成功" : "更新失败");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        boolean success = studentService.removeById(id);
        result.put("success", success);
        result.put("message", success ? "删除成功" : "删除失败");
        return ResponseEntity.ok(result);
    }
}
