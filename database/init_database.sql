-- 实验选课系统数据库
-- MySQL 8.0

-- 创建数据库
CREATE DATABASE IF NOT EXISTS lab_course_system
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE lab_course_system;

-- 删除已有的表（如果存在）
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS score;
DROP TABLE IF EXISTS selection;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS lab;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS admin;

-- 1. 学生表
CREATE TABLE student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_no VARCHAR(20) NOT NULL UNIQUE COMMENT '学号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender VARCHAR(10) COMMENT '性别',
    major VARCHAR(100) COMMENT '专业',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生表';

-- 2. 教师表
CREATE TABLE teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    teacher_no VARCHAR(20) NOT NULL UNIQUE COMMENT '工号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    title VARCHAR(50) COMMENT '职称',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师表';

-- 3. 管理员表
CREATE TABLE admin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 4. 实验室表
CREATE TABLE lab (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lab_name VARCHAR(100) NOT NULL COMMENT '实验室名称',
    location VARCHAR(200) COMMENT '地点',
    capacity INT COMMENT '容量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室表';

-- 5. 课程表
CREATE TABLE course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_name VARCHAR(100) NOT NULL COMMENT '课程名',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    lab_id BIGINT COMMENT '实验室ID',
    course_time VARCHAR(100) COMMENT '上课时间',
    max_count INT DEFAULT 30 COMMENT '最大人数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (teacher_id) REFERENCES teacher(id),
    FOREIGN KEY (lab_id) REFERENCES lab(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 6. 选课表
CREATE TABLE selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    select_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '选课时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (course_id) REFERENCES course(id),
    UNIQUE KEY uk_student_course (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课表';

-- 7. 成绩表
CREATE TABLE score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    score DECIMAL(5,2) COMMENT '成绩',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (course_id) REFERENCES course(id),
    UNIQUE KEY uk_student_course_score (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩表';

-- 8. 考勤表
CREATE TABLE attendance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    attendance_status ENUM('出勤','请假','缺勤','迟到') COMMENT '出勤状态',
    attendance_date DATE COMMENT '考勤日期',
    modified_by BIGINT COMMENT '修改人ID',
    modify_time DATETIME COMMENT '修改时间',
    modify_reason VARCHAR(200) COMMENT '修改原因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (course_id) REFERENCES course(id),
    UNIQUE KEY uk_student_course_date (student_id, course_id, attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤表';

-- 创建索引
CREATE INDEX idx_course_teacher ON course(teacher_id);
CREATE INDEX idx_course_lab ON course(lab_id);
CREATE INDEX idx_selection_student ON selection(student_id);
CREATE INDEX idx_selection_course ON selection(course_id);
CREATE INDEX idx_score_student ON score(student_id);
CREATE INDEX idx_score_course ON score(course_id);
CREATE INDEX idx_attendance_student ON attendance(student_id);
CREATE INDEX idx_attendance_course ON attendance(course_id);

-- 初始数据（密码均已使用BCrypt哈希，原文: 123456）
-- 首次运行时 PasswordMigration 会自动将明文密码升级为BCrypt

-- 管理员数据
INSERT INTO admin (username, password) VALUES
('admin', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq');

-- 教师数据
INSERT INTO teacher (teacher_no, name, title, password) VALUES
('T001', '张三', '教授', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq'),
('T002', '李四', '副教授', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq'),
('T003', '王五', '讲师', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq');

-- 学生数据
INSERT INTO student (student_no, name, gender, major, password) VALUES
('S001', '王小明', '男', '计算机科学与技术', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq'),
('S002', '李小红', '女', '软件工程', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq'),
('S003', '张小强', '男', '网络工程', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq'),
('S004', '刘小芳', '女', '信息安全', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq'),
('S005', '陈小刚', '男', '物联网工程', '$2a$10$xho2DUDIw9hwjxC2e7NPvej93757fkeQJHEGTior0Wt.ViGBRphNq');

-- 实验室数据
INSERT INTO lab (lab_name, location, capacity) VALUES
('计算机实验室A', '信息楼101', 40),
('计算机实验室B', '信息楼102', 35),
('软件工程实验室', '信息楼201', 30),
('网络安全实验室', '信息楼301', 25),
('物联网实验室', '信息楼401', 30);

-- 课程数据
INSERT INTO course (course_name, teacher_id, lab_id, course_time, max_count) VALUES
('Java程序设计', 1, 1, '周一 1-2节', 35),
('数据库原理', 1, 3, '周二 3-4节', 30),
('Web开发技术', 2, 2, '周三 5-6节', 35),
('计算机网络', 2, 4, '周四 7-8节', 30),
('软件测试', 3, 3, '周五 1-2节', 30);

-- 显示数据库结构
SELECT '数据库创建完成！' AS message;
SHOW TABLES;
