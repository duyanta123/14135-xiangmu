package com.labcourse.service.impl;

import com.labcourse.entity.Course;
import com.labcourse.repository.CourseRepository;
import com.labcourse.service.CourseService;
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
public class CourseServiceImpl implements CourseService {

    private static final Logger logger = LoggerFactory.getLogger(CourseServiceImpl.class);

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<Map<String, Object>> getCourseList() {
        String sql = """
            SELECT
                c.id,
                c.course_name,
                t.name AS teacher_name,
                l.lab_name,
                l.location,
                c.course_time,
                c.max_count,
                COUNT(s.id) AS selected_count
            FROM course c
            LEFT JOIN teacher t ON c.teacher_id = t.id
            LEFT JOIN lab l ON c.lab_id = l.id
            LEFT JOIN selection s ON c.id = s.course_id
            GROUP BY c.id, c.course_name, t.name, l.lab_name, l.location, c.course_time, c.max_count
            ORDER BY c.id
            """;
        return jdbcTemplate.queryForList(sql);
    }

    @Override
    public List<Course> list() {
        return courseRepository.findAll();
    }

    @Override
    public boolean save(Course course) {
        courseRepository.save(course);
        return true;
    }

    @Override
    public boolean updateById(Course course) {
        courseRepository.save(course);
        return true;
    }

    @Override
    @Transactional
    public boolean removeById(Long id) {
        if (!courseRepository.existsById(id)) {
            logger.warn("删除课程失败：课程 {} 不存在", id);
            return false;
        }

        // 注意：以下关联表需要手动清理（JPA 未配置级联删除）：
        // - selection (选课表)
        // - score (成绩表)
        // - attendance (考勤表)
        int deletedSelections = jdbcTemplate.update("DELETE FROM selection WHERE course_id = ?", id);
        int deletedScores = jdbcTemplate.update("DELETE FROM score WHERE course_id = ?", id);
        int deletedAttendances = jdbcTemplate.update("DELETE FROM attendance WHERE course_id = ?", id);
        logger.info("删除课程 {} 的关联数据：选课 {} 条，成绩 {} 条，考勤 {} 条",
                id, deletedSelections, deletedScores, deletedAttendances);

        courseRepository.deleteById(id);
        logger.info("课程 {} 删除成功", id);
        return true;
    }
}
