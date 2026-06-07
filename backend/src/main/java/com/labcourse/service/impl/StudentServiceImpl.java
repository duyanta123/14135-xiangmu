package com.labcourse.service.impl;

import com.labcourse.entity.Student;
import com.labcourse.exception.AccountLockedException;
import com.labcourse.repository.StudentRepository;
import com.labcourse.service.LoginAttemptService;
import com.labcourse.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@SuppressWarnings("null")
public class StudentServiceImpl implements StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public Student login(String studentNo, String password) {
        String key = "student:" + studentNo;

        LoginAttemptService.LoginResult checkResult = loginAttemptService.checkLoginAttempt(key);
        if (!checkResult.isAllowed()) {
            throw new AccountLockedException(
                    "账号已被锁定，请" + checkResult.getRemainingLockMinutes() + "分钟后再试",
                    checkResult.getRemainingLockMinutes());
        }

        Optional<Student> studentOpt = studentRepository.findByStudentNo(studentNo);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            if (passwordEncoder.matches(password, student.getPassword())) {
                loginAttemptService.resetAttempts(key);
                return student;
            }
        }

        loginAttemptService.recordFailedAttempt(key);
        return null;
    }

    @Override
    public List<Student> list() {
        return studentRepository.findAll();
    }

    @Override
    public boolean save(Student student) {
        student.setPassword(passwordEncoder.encode(student.getPassword()));
        studentRepository.save(student);
        return true;
    }

    @Override
    public boolean updateById(Student student) {
        Optional<Student> existingOpt = studentRepository.findById(student.getId());
        if (existingOpt.isPresent()) {
            Student existing = existingOpt.get();
            if (student.getStudentNo() != null && !student.getStudentNo().isEmpty()) {
                existing.setStudentNo(student.getStudentNo());
            }
            if (student.getName() != null && !student.getName().isEmpty()) {
                existing.setName(student.getName());
            }
            if (student.getGender() != null) {
                existing.setGender(student.getGender());
            }
            if (student.getMajor() != null) {
                existing.setMajor(student.getMajor());
            }
            if (student.getPassword() != null && !student.getPassword().isEmpty()) {
                existing.setPassword(passwordEncoder.encode(student.getPassword()));
            }
            studentRepository.save(existing);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeById(Long id) {
        studentRepository.deleteById(id);
        return true;
    }
}
