package com.labcourse.controller;

import com.labcourse.service.SelectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/selection")
public class SelectionController {

    @Autowired
    private SelectionService selectionService;

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Long> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = (Long) authentication.getPrincipal();
        Long courseId = data.get("courseId");

        if (courseId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "课程ID不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        Map<String, Object> result = new HashMap<>();
        boolean success = selectionService.addSelection(currentUserId, courseId);

        result.put("success", success);
        result.put("message", success ? "选课成功" : "选课失败，请检查课程是否存在、是否已满或是否已选过");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = (Long) authentication.getPrincipal();

        Map<String, Object> result = new HashMap<>();
        // Security fix (MEDIUM): 传入当前用户ID进行所有权校验，防止IDOR
        boolean success = selectionService.deleteSelection(id, currentUserId);
        result.put("success", success);
        result.put("message", success ? "退课成功" : "退课失败，无权操作此选课记录");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> myCourses() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = (Long) authentication.getPrincipal();
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> courses = selectionService.getMyCourses(currentUserId);
        result.put("success", true);
        result.put("data", courses);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/studentList/{courseId}")
    public ResponseEntity<Map<String, Object>> studentList(@PathVariable Long courseId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> students = selectionService.getStudentList(courseId);
        result.put("success", true);
        result.put("data", students);
        return ResponseEntity.ok(result);
    }
}
