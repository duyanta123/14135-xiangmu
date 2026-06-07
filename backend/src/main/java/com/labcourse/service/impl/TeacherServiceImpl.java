package com.labcourse.service.impl;

import com.labcourse.entity.Teacher;
import com.labcourse.exception.AccountLockedException;
import com.labcourse.repository.TeacherRepository;
import com.labcourse.service.LoginAttemptService;
import com.labcourse.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@SuppressWarnings("null")
public class TeacherServiceImpl implements TeacherService {

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public Teacher login(String teacherNo, String password) {
        String key = "teacher:" + teacherNo;

        LoginAttemptService.LoginResult checkResult = loginAttemptService.checkLoginAttempt(key);
        if (!checkResult.isAllowed()) {
            throw new AccountLockedException(
                    "账号已被锁定，请" + checkResult.getRemainingLockMinutes() + "分钟后再试",
                    checkResult.getRemainingLockMinutes());
        }

        Optional<Teacher> teacherOpt = teacherRepository.findByTeacherNo(teacherNo);
        if (teacherOpt.isPresent()) {
            Teacher teacher = teacherOpt.get();
            if (passwordEncoder.matches(password, teacher.getPassword())) {
                loginAttemptService.resetAttempts(key);
                return teacher;
            }
        }

        loginAttemptService.recordFailedAttempt(key);
        return null;
    }

    @Override
    public List<Teacher> list() {
        return teacherRepository.findAll();
    }

    @Override
    public boolean save(Teacher teacher) {
        teacher.setPassword(passwordEncoder.encode(teacher.getPassword()));
        teacherRepository.save(teacher);
        return true;
    }

    @Override
    public boolean updateById(Teacher teacher) {
        Optional<Teacher> existingOpt = teacherRepository.findById(teacher.getId());
        if (existingOpt.isPresent()) {
            Teacher existing = existingOpt.get();
            if (teacher.getTeacherNo() != null && !teacher.getTeacherNo().isEmpty()) {
                existing.setTeacherNo(teacher.getTeacherNo());
            }
            if (teacher.getName() != null && !teacher.getName().isEmpty()) {
                existing.setName(teacher.getName());
            }
            if (teacher.getTitle() != null) {
                existing.setTitle(teacher.getTitle());
            }
            if (teacher.getPassword() != null && !teacher.getPassword().isEmpty()) {
                existing.setPassword(passwordEncoder.encode(teacher.getPassword()));
            }
            teacherRepository.save(existing);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeById(Long id) {
        teacherRepository.deleteById(id);
        return true;
    }
}
