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
        for (Student student : students) {
            String password = student.getPassword();
            if (password != null && !password.startsWith("$2a$")) {
                student.setPassword(passwordEncoder.encode(password));
                studentRepository.save(student);
            }
        }
    }

    private void migrateTeachers() {
        List<Teacher> teachers = teacherRepository.findAll();
        for (Teacher teacher : teachers) {
            String password = teacher.getPassword();
            if (password != null && !password.startsWith("$2a$")) {
                teacher.setPassword(passwordEncoder.encode(password));
                teacherRepository.save(teacher);
            }
        }
    }

    private void migrateAdmins() {
        List<Admin> admins = adminRepository.findAll();
        for (Admin admin : admins) {
            String password = admin.getPassword();
            if (password != null && !password.startsWith("$2a$")) {
                admin.setPassword(passwordEncoder.encode(password));
                adminRepository.save(admin);
            }
        }
    }
}
