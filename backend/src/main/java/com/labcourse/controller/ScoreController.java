package com.labcourse.controller;

import com.labcourse.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/score")
public class ScoreController {

    @Autowired
    private ScoreService scoreService;

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> data) {
        Long studentId = Long.valueOf(data.get("studentId").toString());
        Long courseId = Long.valueOf(data.get("courseId").toString());
        BigDecimal score = new BigDecimal(data.get("score").toString());

        Map<String, Object> result = new HashMap<>();
        boolean success = scoreService.addScore(studentId, courseId, score);

        result.put("success", success);
        result.put("message", success ? "成绩录入成功" : "成绩录入失败");
        return ResponseEntity.ok(result);
    }
}
