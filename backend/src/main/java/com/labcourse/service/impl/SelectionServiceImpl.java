package com.labcourse.service.impl;

import com.labcourse.entity.Course;
import com.labcourse.entity.Selection;
import com.labcourse.repository.CourseRepository;
import com.labcourse.repository.SelectionRepository;
import com.labcourse.service.SelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings("null")
public class SelectionServiceImpl implements SelectionService {

    private static final Logger logger = LoggerFactory.getLogger(SelectionServiceImpl.class);

    @Autowired
    private SelectionRepository selectionRepository;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public boolean addSelection(Long studentId, Long courseId) {
        try {
            if (selectionRepository.findByStudentIdAndCourseId(studentId, courseId).isPresent()) {
                logger.warn("选课失败：学生 {} 已选择课程 {}", studentId, courseId);
                return false;
            }

            Long count = selectionRepository.countByCourseId(courseId);
            Course course = courseRepository.findById(courseId).orElse(null);
            
            if (course == null) {
                logger.warn("选课失败：课程 {} 不存在", courseId);
                return false;
            }
            
            if (count >= course.getMaxCount()) {
                logger.warn("选课失败：课程 {} 人数已满 (已选 {} / 上限 {})", courseId, count, course.getMaxCount());
                return false;
            }

            Selection selection = new Selection();
            selection.setStudentId(studentId);
            selection.setCourseId(courseId);
            selectionRepository.save(selection);
            logger.info("选课成功：学生 {} 选择了课程 {} ({})", studentId, courseId, course.getCourseName());
            return true;
        } catch (Exception e) {
            logger.error("选课异常：学生 {} 课程 {} - {}", studentId, courseId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteSelection(Long id, Long currentUserId) {
        // Security fix (MEDIUM): 验证选课记录所有权，防止IDOR攻击
        com.labcourse.entity.Selection selection = selectionRepository.findById(id).orElse(null);
        if (selection == null || !selection.getStudentId().equals(currentUserId)) {
            logger.warn("退课失败：选课记录 {} 不属于学生 {} 或不存在", id, currentUserId);
            return false;
        }
        selectionRepository.deleteById(id);
        logger.info("学生 {} 退课成功：选课记录 {}", currentUserId, id);
        return true;
    }

    @Override
    public List<Map<String, Object>> getMyCourses(Long studentId) {
        String sql = """
            SELECT
                sel.id AS selection_id,
                c.id AS course_id,
                c.course_name,
                t.name AS teacher_name,
                l.lab_name,
                l.location,
                c.course_time,
                sel.select_time
            FROM selection sel
            JOIN course c ON sel.course_id = c.id
            JOIN teacher t ON c.teacher_id = t.id
            LEFT JOIN lab l ON c.lab_id = l.id
            WHERE sel.student_id = ?
            ORDER BY sel.select_time DESC
            """;
        return jdbcTemplate.queryForList(sql, studentId);
    }

    @Override
    public List<Map<String, Object>> getStudentList(Long courseId) {
        String sql = """
            SELECT
                s.id AS student_id,
                s.student_no,
                s.name,
                s.gender,
                s.major,
                sel.select_time
            FROM selection sel
            JOIN student s ON sel.student_id = s.id
            WHERE sel.course_id = ?
            ORDER BY s.student_no
            """;
        return jdbcTemplate.queryForList(sql, courseId);
    }
}
