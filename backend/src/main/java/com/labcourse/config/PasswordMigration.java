package com.labcourse.config;

import com.labcourse.entity.Admin;
import com.labcourse.entity.Student;
import com.labcourse.entity.Teacher;
import com.labcourse.repository.AdminRepository;
import com.labcourse.repository.StudentRepository;
import com.labcourse.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PasswordMigration implements CommandLineRunner {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        migrateStudents();
        migrateTeachers();
        migrateAdmins();
    }

    private void migrateStudents() {
        List<Student> students = studentRepository.findAll();
        int migrated = 0;
        for (Student student : students) {
            String password = student.getPassword();
            // 检测所有BCrypt变体：$2a$, $2b$, $2y$
            if (password != null && !password.startsWith("$2")) {
                student.setPassword(passwordEncoder.encode(password));
                studentRepository.save(student);
                migrated++;
            }
        }
        if (migrated > 0) {
            System.out.println("[PasswordMigration] 已迁移 " + migrated + " 个学生密码至BCrypt");
        }
    }

    private void migrateTeachers() {
        List<Teacher> teachers = teacherRepository.findAll();
        int migrated = 0;
        for (Teacher teacher : teachers) {
            String password = teacher.getPassword();
            if (password != null && !password.startsWith("$2")) {
                teacher.setPassword(passwordEncoder.encode(password));
                teacherRepository.save(teacher);
                migrated++;
            }
        }
        if (migrated > 0) {
            System.out.println("[PasswordMigration] 已迁移 " + migrated + " 个教师密码至BCrypt");
        }
    }

    private void migrateAdmins() {
        List<Admin> admins = adminRepository.findAll();
        int migrated = 0;
        for (Admin admin : admins) {
            String password = admin.getPassword();
            if (password != null && !password.startsWith("$2")) {
                admin.setPassword(passwordEncoder.encode(password));
                adminRepository.save(admin);
                migrated++;
            }
        }
        if (migrated > 0) {
            System.out.println("[PasswordMigration] 已迁移 " + migrated + " 个管理员密码至BCrypt");
        }
    }
}
