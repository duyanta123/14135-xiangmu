package com.labcourse.config;

import com.labcourse.exception.AccountLockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        result.put("success", false);
        result.put("message", "参数验证失败");
        result.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", ex.getMessage() != null ? ex.getMessage() : "非法参数");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLockedException(
            AccountLockedException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", ex.getMessage());
        result.put("locked", true);
        result.put("remainingMinutes", ex.getRemainingMinutes());
        return ResponseEntity.status(423).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "服务器内部错误");
        // 不要返回详细的错误信息给前端，记录到日志即可
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
