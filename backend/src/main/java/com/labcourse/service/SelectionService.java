package com.labcourse.service;

import java.util.List;
import java.util.Map;

public interface SelectionService {
    boolean addSelection(Long studentId, Long courseId);
    boolean deleteSelection(Long id, Long currentUserId);
    List<Map<String, Object>> getMyCourses(Long studentId);
    List<Map<String, Object>> getStudentList(Long courseId);
}
