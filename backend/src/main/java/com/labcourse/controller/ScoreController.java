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
        Map<String, Object> result = new HashMap<>();

        // Security fix (MED-005): 输入验证
        Object studentIdObj = data.get("studentId");
        Object courseIdObj = data.get("courseId");
        Object scoreObj = data.get("score");

        if (studentIdObj == null || courseIdObj == null || scoreObj == null) {
            result.put("success", false);
            result.put("message", "studentId, courseId, score 不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        Long studentId = Long.valueOf(studentIdObj.toString());
        Long courseId = Long.valueOf(courseIdObj.toString());
        BigDecimal score = new BigDecimal(scoreObj.toString());

        // 成绩值校验：0-100
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
            result.put("success", false);
            result.put("message", "成绩必须在0-100之间");
            return ResponseEntity.badRequest().body(result);
        }

        // ID校验：必须为正数
        if (studentId <= 0 || courseId <= 0) {
            result.put("success", false);
            result.put("message", "无效的ID参数");
            return ResponseEntity.badRequest().body(result);
        }

        boolean success = scoreService.addScore(studentId, courseId, score);

        result.put("success", success);
        result.put("message", success ? "成绩录入成功" : "成绩录入失败");
        return ResponseEntity.ok(result);
    }
}
