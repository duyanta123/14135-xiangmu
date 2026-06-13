package com.labcourse;

import com.labcourse.config.PasswordMigration;
import com.labcourse.entity.Admin;
import com.labcourse.entity.Student;
import com.labcourse.entity.Teacher;
import com.labcourse.repository.AdminRepository;
import com.labcourse.repository.StudentRepository;
import com.labcourse.repository.TeacherRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PasswordMigrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Test
    @DisplayName("Security: 所有学生密码在迁移后均为BCrypt哈希")
    void testAllStudentPasswordsAreBCryptHashed() {
        for (Student student : studentRepository.findAll()) {
            String password = student.getPassword();
            // BCrypt hashes start with $2a$, $2b$, or $2y$
            assertTrue(password != null && password.startsWith("$2"),
                    "学生 " + student.getStudentNo() + " 的密码未使用BCrypt哈希: " + password);
        }
    }

    @Test
    @DisplayName("Security: 所有教师密码在迁移后均为BCrypt哈希")
    void testAllTeacherPasswordsAreBCryptHashed() {
        for (Teacher teacher : teacherRepository.findAll()) {
            String password = teacher.getPassword();
            assertTrue(password != null && password.startsWith("$2"),
                    "教师 " + teacher.getTeacherNo() + " 的密码未使用BCrypt哈希: " + password);
        }
    }

    @Test
    @DisplayName("Security: 所有管理员密码在迁移后均为BCrypt哈希")
    void testAllAdminPasswordsAreBCryptHashed() {
        for (Admin admin : adminRepository.findAll()) {
            String password = admin.getPassword();
            assertTrue(password != null && password.startsWith("$2"),
                    "管理员 " + admin.getUsername() + " 的密码未使用BCrypt哈希: " + password);
        }
    }

    @Test
    @DisplayName("Security: 密码不包含明文")
    void testNoPlaintextPasswords() {
        for (Student student : studentRepository.findAll()) {
            String password = student.getPassword();
            assertFalse("123456".equals(password) || "admin".equals(password),
                    "发现明文密码: " + student.getStudentNo());
        }
        for (Teacher teacher : teacherRepository.findAll()) {
            String password = teacher.getPassword();
            assertFalse("123456".equals(password) || "admin".equals(password),
                    "发现明文密码: " + teacher.getTeacherNo());
        }
        for (Admin admin : adminRepository.findAll()) {
            String password = admin.getPassword();
            assertFalse("123456".equals(password) || "admin".equals(password),
                    "发现明文密码: " + admin.getUsername());
        }
    }
}