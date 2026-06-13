# 实验选课系统 — Code Wiki

> **版本**: 1.0.0 | **最后更新**: 2026-06-13 | **文档类型**: 结构化代码知识库

---

## 目录

1. [项目概述](#1-项目概述)
2. [系统架构](#2-系统架构)
3. [项目目录结构](#3-项目目录结构)
4. [后端模块 (Spring Boot)](#4-后端模块-spring-boot)
   - 4.1 [启动入口](#41-启动入口)
   - 4.2 [配置层 (config)](#42-配置层-config)
   - 4.3 [过滤器 (filter)](#43-过滤器-filter)
   - 4.4 [控制器层 (controller)](#44-控制器层-controller)
   - 4.5 [服务层 (service)](#45-服务层-service)
   - 4.6 [数据访问层 (repository)](#46-数据访问层-repository)
   - 4.7 [实体层 (entity)](#47-实体层-entity)
   - 4.8 [异常处理 (exception)](#48-异常处理-exception)
   - 4.9 [工具类 (util)](#49-工具类-util)
5. [BFF 中间层 (Fastify)](#5-bff-中间层-fastify)
   - 5.1 [入口与配置](#51-入口与配置)
   - 5.2 [路由 (routes)](#52-路由-routes)
   - 5.3 [中间件 (middleware)](#53-中间件-middleware)
   - 5.4 [代理层 (proxy)](#54-代理层-proxy)
   - 5.5 [服务 (services)](#55-服务-services)
   - 5.6 [工具 (utils)](#56-工具-utils)
6. [前端模块 (Vue 3)](#6-前端模块-vue-3)
   - 6.1 [入口与配置](#61-入口与配置)
   - 6.2 [API 封装层 (api)](#62-api-封装层-api)
   - 6.3 [路由 (router)](#63-路由-router)
   - 6.4 [工具模块 (utils)](#64-工具模块-utils)
   - 6.5 [视图组件 (views)](#65-视图组件-views)
7. [数据库设计](#7-数据库设计)
   - 7.1 [ER 图](#71-er-图)
   - 7.2 [表结构详细说明](#72-表结构详细说明)
8. [安全体系](#8-安全体系)
9. [CI/CD 持续集成](#9-cicd-持续集成)
10. [项目运行方式](#10-项目运行方式)
11. [依赖关系图](#11-依赖关系图)

---

## 1. 项目概述

实验选课系统是一个面向**高校实验室课程管理**的完整 Web 应用，采用 **前后端分离 + BFF 中间层** 的三层架构。系统为学生、教师和管理员三类角色提供一站式实验课程管理解决方案，涵盖选课、考勤签到、成绩管理、课表展示、系统管理等核心场景。

### 技术栈总览

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.0 |
| 语言 | Java | 17 |
| 安全框架 | Spring Security + JJWT | 6.2.0 / 0.12.3 |
| ORM | Spring Data JPA (Hibernate) | 3.2.0 / 6.3.1 |
| 数据库 | MySQL | 8.0 |
| 连接池 | HikariCP | — |
| 构建工具 | Maven | 3.9 |
| BFF 中间层 | Fastify (Node.js) | 4.28.0 |
| 前端框架 | Vue 3 (Composition API) | 3.4 |
| 构建工具 | Vite | 5.0.8 |
| UI 组件库 | Element Plus | 2.4.4 |
| HTTP 客户端 | Axios | 1.6.2 |
| Excel 导出 | XLSX | 0.18.5 |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                   前端 (Vue 3 + Vite) :3000                     │
│  Login → Student Views / Teacher Views / Admin Views           │
│  Axios + TokenManager + OfflineQueue + ScheduleParser          │
└────────────────────────────┬────────────────────────────────────┘
                             │ Vite Proxy /api → :4000 (BFF)
┌────────────────────────────┼────────────────────────────────────┐
│                  BFF 中间层 (Fastify) :4000                     │
│  CORS → Cookie → requestLogger → JWT验证 → 透明代理            │
│  /api/auth/* (自处理)    /api/* → 转发后端                      │
└────────────────────────────┼────────────────────────────────────┘
                             │ HTTP Proxy → :8080
┌────────────────────────────┼────────────────────────────────────┐
│                   后端 (Spring Boot) :8080                      │
│  JwtFilter → SecurityConfig(RBAC) → Controller → Service       │
│  → Repository (JPA) → MySQL                                    │
│  GlobalExceptionHandler / PasswordMigration                    │
└────────────────────────────┼────────────────────────────────────┘
                             │
                     ┌───────┴───────┐
                     │   MySQL 8.0   │
                     │  8 张数据表    │
                     └───────────────┘
```

### 2.2 请求流程

```
用户浏览器 → :3000(Vite Dev Server) → Vite代理 /api → :4000(BFF)
  → Fastify Server
    ├─ 公开路由（登录/课程列表）→ 直接转发或处理
    ├─ 认证路由 → jwtVerify中间件 → 验证HttpOnly Cookie
    └─ 其他 → transparentProxy → 附加Authorization头 → :8080(Spring Boot)
      → JwtFilter(提取Token→设置SecurityContext)
      → SecurityConfig(RBAC权限校验)
      → Controller → Service → Repository → MySQL
      → 响应逐层返回
```

### 2.3 BFF 模式与降级模式

Vite 配置支持两种模式切换：

- **BFF 模式** (`VITE_BFF_ENABLED=true`, 默认): 前端请求经 `:3000 → :4000 → :8080`，Token 存储在 HttpOnly Cookie 中
- **降级模式** (`VITE_BFF_ENABLED=false`): 前端直接代理到 `:3000 → :8080`，Token 存储在 localStorage 中

---

## 3. 项目目录结构

```
d:\789\
├── .github/workflows/
│   └── attendance-ci.yml              # GitHub Actions CI 流水线
│
├── backend/                           # Spring Boot 后端
│   ├── pom.xml                        # Maven 依赖配置
│   └── src/
│       ├── main/java/com/labcourse/
│       │   ├── LabCourseApplication.java    # 启动类
│       │   ├── config/                      # 配置层
│       │   │   ├── SecurityConfig.java
│       │   │   ├── GlobalExceptionHandler.java
│       │   │   └── PasswordMigration.java
│       │   ├── filter/
│       │   │   └── JwtFilter.java           # JWT 认证过滤器
│       │   ├── controller/                  # 控制器层 (9 个)
│       │   ├── service/                     # 服务接口 (9 个)
│       │   │   ├── impl/                    # 服务实现 (8 个)
│       │   │   └── LoginAttemptService.java
│       │   ├── repository/                  # 数据访问层 (8 个)
│       │   ├── entity/                      # 实体类 (9 个)
│       │   ├── exception/
│       │   │   └── AccountLockedException.java
│       │   └── util/
│       │       └── JwtUtil.java
│       ├── main/resources/
│       │   ├── application.yml              # 默认配置
│       │   └── application-prod.yml         # 生产配置
│       └── test/java/com/labcourse/        # 测试 (5 类, 73 用例)
│
├── bff/                                # Fastify BFF 中间层
│   ├── package.json
│   ├── .env / .env.example
│   └── src/
│       ├── index.js                    # 入口
│       ├── config.js                   # 配置
│       ├── routes/auth.js              # 认证路由
│       ├── middleware/
│       │   ├── jwtVerify.js
│       │   ├── errorHandler.js
│       │   └── requestLogger.js
│       ├── proxy/
│       │   ├── transparentProxy.js     # 透明代理
│       │   └── proxyMapping.js         # 路由映射
│       ├── services/backendClient.js   # 后端 HTTP 客户端
│       └── utils/logger.js             # 日志工具
│
├── frontend/                           # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── main.js                     # 入口
│       ├── App.vue                     # 根组件
│       ├── api/                        # API 封装 (9 个)
│       ├── router/index.js             # 路由 + 导航守卫
│       ├── utils/                      # 工具模块 (7 个)
│       ├── views/                      # 视图组件 (16 个)
│       │   ├── Login.vue
│       │   ├── student/ (6)  teacher/ (5)  admin/ (5)
│       └── styles/global.css
│
├── database/                           # 数据库脚本
│   ├── init_database.sql               # 初始化脚本（建表+种子数据）
│   └── queries.sql                     # 常用查询示例
│
└── README.md                           # 项目说明文档
```

---

## 4. 后端模块 (Spring Boot)

### 4.1 启动入口

**文件**: [`LabCourseApplication.java`](file:///d:/789/backend/src/main/java/com/labcourse/LabCourseApplication.java)

```java
@SpringBootApplication
public class LabCourseApplication {
    public static void main(String[] args) {
        SpringApplication.run(LabCourseApplication.class, args);
    }
}
```

标准 Spring Boot 启动类，自动扫描 `com.labcourse` 包下的所有组件。

---

### 4.2 配置层 (config)

#### SecurityConfig

**文件**: [`SecurityConfig.java`](file:///d:/789/backend/src/main/java/com/labcourse/config/SecurityConfig.java)

| 职责 | 说明 |
|------|------|
| RBAC 权限控制 | 基于 Spring Security 的接口级细粒度角色控制 |
| BCrypt 加密 | 提供 `PasswordEncoder` Bean (BCryptPasswordEncoder) |
| CORS 配置 | 允许 `localhost:3000` 和 `localhost:4000` 跨域 |
| 会话管理 | 无状态模式 (SessionCreationPolicy.STATELESS) |
| JWT 集成 | 通过 `addFilterBefore` 在 `UsernamePasswordAuthenticationFilter` 之前注册 `JwtFilter` |
| CSRF | 关闭 (前后端分离不需要) |

**接口权限矩阵**:

| 路由前缀 | 角色要求 | 说明 |
|----------|----------|------|
| `/api/student/login`, `/api/teacher/login`, `/api/admin/login` | 匿名 | 登录接口 |
| `/api/course/list`, `/api/course/list/simple` | 匿名 | 课程列表 |
| `/api/auth/refresh`, `/api/auth/validate` | 认证即可 | Token 刷新/验证 |
| `/api/student/add,update,delete,list` | ROLE_admin | 学生管理 |
| `/api/teacher/add,update,delete,list` | ROLE_admin | 教师管理 |
| `/api/course/add,update,delete` | ROLE_admin | 课程管理 |
| `/api/lab/**` | ROLE_admin | 实验室管理 |
| `/api/selection/add,my,delete` | ROLE_student | 选课操作 |
| `/api/attendance/check-in,history,server-time` | ROLE_student | 学生考勤 |
| `/api/selection/studentList`, `/api/score/**` | ROLE_teacher | 教师查看学生/成绩 |
| `/api/attendance/course,dates,update-status,export` | ROLE_teacher | 教师考勤管理 |

#### GlobalExceptionHandler

**文件**: [`GlobalExceptionHandler.java`](file:///d:/789/backend/src/main/java/com/labcourse/config/GlobalExceptionHandler.java)

全局异常拦截器，使用 `@RestControllerAdvice` 注解，统一处理三类异常：

| 异常类型 | HTTP 状态码 | 说明 |
|----------|-------------|------|
| `MethodArgumentNotValidException` | 400 | 参数校验失败，返回字段级错误详情 |
| `IllegalArgumentException` | 400 | 非法参数，返回错误消息 |
| `AccountLockedException` | 423 | 账号锁定，返回剩余锁定分钟数 |
| `Exception` (通用) | 500 | 服务器内部错误 |

#### PasswordMigration

**文件**: [`PasswordMigration.java`](file:///d:/789/backend/src/main/java/com/labcourse/config/PasswordMigration.java)

实现 `CommandLineRunner`，在应用启动时自动执行。检查数据库中所有学生、教师、管理员密码，将未被 BCrypt 加密的密码（不以 `$2a$` 开头）自动升级为 BCrypt 哈希。该迁移**只在首次启动时生效**，后续启动不会重复执行。

---

### 4.3 过滤器 (filter)

#### JwtFilter

**文件**: [`JwtFilter.java`](file:///d:/789/backend/src/main/java/com/labcourse/filter/JwtFilter.java)

| 属性 | 说明 |
|------|------|
| 基类 | `OncePerRequestFilter` (每个请求执行一次) |
| 执行位置 | 在 `UsernamePasswordAuthenticationFilter` 之前 |
| 核心逻辑 | 从 `Authorization: Bearer <token>` 头提取 Token → `JwtUtil.validateToken()` → 提取 userId 和 role → 构建 `UsernamePasswordAuthenticationToken` → 设置到 `SecurityContextHolder` |
| 异常处理 | 验证失败不抛异常，仅记录日志，让请求继续（由 SecurityConfig 决定是否允许） |

**核心方法**: `doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)`

---

### 4.4 控制器层 (controller)

#### AuthController

**文件**: [`AuthController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/AuthController.java)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/auth/refresh` | POST | 刷新 Token（需当前有效 Token） |
| `/api/auth/validate` | GET | 验证 Token 有效性，返回剩余时间 |

#### AdminController

**文件**: [`AdminController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/AdminController.java)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/login` | POST | 管理员登录，返回用户信息和 Token |

**登录逻辑**: 参数校验(用户名/密码非空) → LoginAttemptService 检查是否锁定 → 密码匹配 → 生成 JWT Token → 重置失败计数

#### StudentController

**文件**: [`StudentController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/StudentController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/student/login` | POST | 匿名 | 学生登录 |
| `/api/student/list` | GET | admin | 查询所有学生 |
| `/api/student/save` | POST | admin | 新增学生 |
| `/api/student/update` | PUT | admin | 更新学生信息 |
| `/api/student/{id}` | DELETE | admin | 删除学生 |

#### TeacherController

**文件**: [`TeacherController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/TeacherController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/teacher/login` | POST | 匿名 | 教师登录 |
| `/api/teacher/list` | GET | admin | 查询所有教师 |
| `/api/teacher/save` | POST | admin | 新增教师 |
| `/api/teacher/update` | PUT | admin | 更新教师信息 |
| `/api/teacher/{id}` | DELETE | admin | 删除教师 |

#### CourseController

**文件**: [`CourseController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/CourseController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/course/list` | GET | 公开 | 查询所有课程（含教师、实验室、选课人数） |
| `/api/course/list/simple` | GET | 公开 | 简化课程列表 |
| `/api/course/save` | POST | admin | 新增课程 |
| `/api/course/update` | PUT | admin | 更新课程 |
| `/api/course/{id}` | DELETE | admin | 删除课程 |

#### LabController

**文件**: [`LabController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/LabController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/lab/list` | GET | admin | 查询所有实验室 |
| `/api/lab/save` | POST | admin | 新增实验室 |
| `/api/lab/update` | PUT | admin | 更新实验室 |
| `/api/lab/{id}` | DELETE | admin | 删除实验室 |

#### SelectionController

**文件**: [`SelectionController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/SelectionController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/selection/add` | POST | student | 添加选课（返回选课结果或冲突课程名） |
| `/api/selection/delete/{id}` | DELETE | student | 删除选课 |
| `/api/selection/my/{studentId}` | GET | student | 查询某学生的已选课程 |
| `/api/selection/studentList/{courseId}` | GET | teacher | 查询某课程的选课学生列表 |

#### ScoreController

**文件**: [`ScoreController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/ScoreController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/score/add` | POST | teacher | 录入/更新学生成绩 |
| `/api/score/list` | GET | teacher | 查询所有成绩记录 |

#### AttendanceController

**文件**: [`AttendanceController.java`](file:///d:/789/backend/src/main/java/com/labcourse/controller/AttendanceController.java)

| 端点 | 方法 | 角色 | 说明 |
|------|------|------|------|
| `/api/attendance/check-in` | POST | student | 学生签到（系统自动判定出勤/迟到） |
| `/api/attendance/history` | GET | student | 查询某学生的签到历史 |
| `/api/attendance/course` | GET | teacher | 按日期查询课程考勤详情 |
| `/api/attendance/dates` | GET | teacher | 获取课程的所有考勤日期列表 |
| `/api/attendance/update-status` | PUT | teacher | 修改考勤状态（仅缺勤→请假） |
| `/api/attendance/export` | GET | teacher | 导出考勤数据 |
| `/api/attendance/server-time` | GET | student | 获取服务器当前时间 |

---

### 4.5 服务层 (service)

#### 服务接口与实现映射

| 接口文件 | 实现类文件 | 核心功能 |
|----------|-----------|----------|
| `AdminService.java` | `AdminServiceImpl.java` | 管理员登录、增删改查 |
| `StudentService.java` | `StudentServiceImpl.java` | 学生登录、增删改查 |
| `TeacherService.java` | `TeacherServiceImpl.java` | 教师登录、增删改查 |
| `CourseService.java` | `CourseServiceImpl.java` | 课程增删改查 |
| `LabService.java` | `LabServiceImpl.java` | 实验室增删改查 |
| `SelectionService.java` | `SelectionServiceImpl.java` | 选课/退课业务逻辑 |
| `ScoreService.java` | `ScoreServiceImpl.java` | 成绩录入/更新 |
| `AttendanceService.java` | `AttendanceServiceImpl.java` | 考勤签到核心逻辑 |
| `LoginAttemptService.java` | — | 登录失败限制服务 |

#### AttendanceServiceImpl — 核心签到逻辑详解

**文件**: [`AttendanceServiceImpl.java`](file:///d:/789/backend/src/main/java/com/labcourse/service/impl/AttendanceServiceImpl.java)

这是系统最核心的服务实现，包含以下关键方法：

| 方法 | 说明 |
|------|------|
| `checkIn(Long studentId, Long courseId)` | 签到主流程（事务性） |
| `getStudentHistory(Long studentId)` | 查询学生签到历史 |
| `getCourseAttendance(Long courseId, LocalDate date)` | 教师查询课程考勤 |
| `updateAttendanceStatus(...)` | 修改考勤状态 |
| `getAttendanceDates(Long courseId)` | 获取考勤日期列表 |
| `exportAttendance(Long courseId)` | 导出考勤数据 |
| `getServerTime()` | 获取服务器时间 |

**签到流程 (checkIn)**:

```
1. 获取课程信息 → 不存在则返回"课程不存在"
2. 校验学生存在性 → 不存在则返回"学生不存在"
3. 重复签到检查 → 使用悲观写锁 (SELECT ... FOR UPDATE) 防并发
4. 解析课程时间 → 从 "周一 1-2节" 格式匹配今天
5. 智能状态判定:
   ├─ 课前 0~10 分钟签到 → 出勤
   ├─ 课后 0~3 分钟签到 → 迟到 (容忍期)
   ├─ 课后 >3 分钟签到 → 迟到 (超时)
   └─ 课前 >10 分钟签到 → 出勤 (过早)
6. 创建签到记录
```

**节次-时间映射表**:

| 节次 | 开始时间 |
|------|---------|
| 1 | 08:00 |
| 3 | 10:00 |
| 5 | 14:00 |
| 7 | 16:00 |
| 9 | 19:00 |

**状态修改规则**: 仅允许将"缺勤"修改为"请假"，其他转换一律拒绝。修改时记录 `modifiedBy`、`modifyTime`、`modifyReason`。

#### LoginAttemptService

**文件**: [`LoginAttemptService.java`](file:///d:/789/backend/src/main/java/com/labcourse/service/LoginAttemptService.java)

| 参数 | 值 |
|------|-----|
| 最大尝试次数 | 5 次 |
| 统计窗口 | 15 分钟 |
| 锁定时间 | 30 分钟 |
| 存储方式 | ConcurrentHashMap (内存) |

**核心方法**:

| 方法 | 说明 |
|------|------|
| `checkLoginAttempt(String key)` | 检查是否可以登录 |
| `recordFailedAttempt(String key)` | 记录失败尝试，达到上限自动锁定 |
| `resetAttempts(String key)` | 登录成功后重置计数 |
| `getRemainingAttempts(String key)` | 获取剩余尝试次数 |

**内部类 `LoginAttempt`**: 记录失败次数、首次失败时间、锁定截止时间，提供锁定期和窗口期的计算逻辑。

**内部类 `LoginResult`**: 结果封装，包含 `allowed`、`locked`、`remainingLockMinutes`、`remainingAttempts` 字段。

---

### 4.6 数据访问层 (repository)

全部使用 Spring Data JPA 的 `JpaRepository` 接口，共 8 个：

| Repository | 对应的实体 | 关键自定义查询方法 |
|------------|-----------|-------------------|
| `AdminRepository` | Admin | `findByUsername(String username)` |
| `StudentRepository` | Student | `findByStudentNo(String studentNo)` |
| `TeacherRepository` | Teacher | `findByTeacherNo(String teacherNo)` |
| `CourseRepository` | Course | `findByTeacherId(Long teacherId)` |
| `LabRepository` | Lab | 无自定义方法 |
| `SelectionRepository` | Selection | `findByStudentId()`, `findByCourseId()`, `findByStudentIdAndCourseId()`, `countByCourseId()` |
| `ScoreRepository` | Score | `findByStudentIdAndCourseId()` |
| `AttendanceRepository` | Attendance | `findByStudentIdAndCourseIdAndAttendanceDate()`, `findByStudentIdAndCourseIdAndAttendanceDateForUpdate()`, `findByCourseIdAndAttendanceDate()`, `findByStudentIdOrderByAttendanceDateDesc()`, `findByCourseIdOrderByAttendanceDateDesc()` |

---

### 4.7 实体层 (entity)

| 实体类 | 对应表 | 核心字段 |
|--------|--------|----------|
| `Admin` | admin | id, username, password |
| `Student` | student | id, studentNo, name, gender, major, password |
| `Teacher` | teacher | id, teacherNo, name, title, password |
| `Course` | course | id, courseName, teacherId, labId, courseTime, maxCount |
| `Lab` | lab | id, labName, location, capacity |
| `Selection` | selection | id, studentId, courseId, selectTime |
| `Score` | score | id, studentId, courseId, score |
| `Attendance` | attendance | id, studentId, courseId, attendanceStatus, attendanceDate, modifiedBy, modifyTime, modifyReason |
| `AttendanceStatus` | — (枚举) | 出勤, 请假, 缺勤, 迟到 |

所有实体类都使用 `@PrePersist` / `@PreUpdate` 自动管理 `createdAt` / `updatedAt` 时间戳。

---

### 4.8 异常处理 (exception)

#### AccountLockedException

**文件**: [`AccountLockedException.java`](file:///d:/789/backend/src/main/java/com/labcourse/exception/AccountLockedException.java)

```java
public class AccountLockedException extends RuntimeException {
    private final long remainingMinutes;
    // 构造函数 + getter
}
```

由 `LoginAttemptService` 抛出，在 `GlobalExceptionHandler` 中捕获，返回 HTTP 423 锁定的标准化响应。

---

### 4.9 工具类 (util)

#### JwtUtil

**文件**: [`JwtUtil.java`](file:///d:/789/backend/src/main/java/com/labcourse/util/JwtUtil.java)

| 方法 | 说明 |
|------|------|
| `generateToken(Long userId, String username, String role)` | 生成 JWT，包含 userId、username、role 三个 Claims |
| `extractClaims(String token)` | 解析 Token，返回 Claims |
| `extractUserId(String token)` | 提取 userId |
| `extractRole(String token)` | 提取角色 |
| `extractUsername(String token)` | 提取用户名 |
| `validateToken(String token)` | 验证 Token 是否有效 |
| `isTokenExpired(String token)` | 检查是否过期 |
| `getRemainingTime(String token)` | 获取剩余有效时间（秒） |
| `isTokenAboutToExpire(String token, long thresholdSeconds)` | 检查是否快过期 |

**配置项** (`application.yml`):

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `jwt.secret` | `lab-course-system-secret-key-2024-very-long-secret` | 签名密钥 |
| `jwt.expiration` | `86400000` (24h) | Token 有效期(毫秒) |

**签名算法**: HS256 (HMAC-SHA256)

---

## 5. BFF 中间层 (Fastify)

BFF (Backend For Frontend) 层是基于 **Fastify 4.x** 构建的 Node.js 中间服务，运行在 `:4000`。主要职责：

- 作为前端与后端的**认证网关**，将 JWT Token 存储在 HttpOnly Cookie 中
- 对外提供**统一的 API 入口**，对内透明代理到 Spring Boot
- 处理**登录**、**Token 刷新**、**登出**等认证相关操作
- 提供**请求日志**、**错误处理**等横切关注点

### 5.1 入口与配置

#### index.js

**文件**: [`index.js`](file:///d:/789/bff/src/index.js)

核心函数 `buildApp()` 构建 Fastify 应用：

1. 注册插件：`@fastify/cors`、`@fastify/cookie`、`@fastify/formbody`
2. 注册请求日志钩子
3. 设置认证路由 (`/api/student/login`, `/api/teacher/login`, `/api/admin/login`, `/api/auth/refresh`, `/api/auth/logout`)
4. 注册透明代理插件 (处理其他所有 `/api/*` 请求)
5. 设置全局错误处理

导出 `buildApp()` 用于测试，直接运行时调用 `start()` 启动。

#### config.js

**文件**: [`config.js`](file:///d:/789/bff/src/config.js)

| 配置项 | 环境变量 | 默认值 |
|--------|----------|--------|
| 端口 | `BFF_PORT` | `4000` |
| 后端地址 | `BACKEND_URL` | `http://localhost:8080` |
| 运行环境 | `NODE_ENV` | `development` |
| JWT 密钥 | `JWT_SECRET` | (与后端一致) |
| JWT 有效期 | `JWT_EXPIRATION` | `86400000` (24h) |
| Cookie 名称 | — | `bff_token` |
| Cookie 有效期 | — | 24 小时 |
| 日志级别 | `LOG_LEVEL` | `info` |

---

### 5.2 路由 (routes)

#### auth.js

**文件**: [`auth.js`](file:///d:/789/bff/src/routes/auth.js)

**`createLoginHandler` 工厂函数**: 创建通用的登录处理函数。

```
登录流程:
  参数校验(用户名+密码非空) 
  → 转发请求到后端 (/api/student|teacher|admin/login) 
  → 成功后从响应提取 Token 
  → 签发 HttpOnly Cookie (bff_token) 
  → 返回用户信息(不含 Token，前端通过 Cookie 使用)
  → 失败则返回 401 或 502
```

三个登录路由使用不同的 `usernameField`:
- `/api/student/login` → `studentNo`
- `/api/teacher/login` → `teacherNo`
- `/api/admin/login` → `username`

**`/api/auth/refresh`**: 验证 Cookie 中的 Token → BFF 自主签发新 Token（共享同一 JWT 密钥） → 更新 Cookie

**`/api/auth/logout`**: 清除 `bff_token` Cookie

---

### 5.3 中间件 (middleware)

#### jwtVerify (JWT 验证)

**文件**: [`jwtVerify.js`](file:///d:/789/bff/src/middleware/jwtVerify.js)

| 功能 | 说明 |
|------|------|
| Token 来源 | 优先从 Cookie (`bff_token`) 读取，fallback 到 `Authorization: Bearer` 头 |
| 验证逻辑 | 使用 `jsonwebtoken.verify()` 验证签名和过期时间 |
| Token 透传 | 验证通过后将 Token 重新附加到 `request.headers.authorization`，以便透传后端 |
| 用户注入 | 验证通过后设置 `request.user = { userId, username, role }` |
| 错误处理 | 过期返回 401 + "Token 已过期"，无效返回 401 + "Token 无效" |

**`optionalJwt`**: 可选的 JWT 验证，无论成功失败都继续处理。

#### errorHandler

**文件**: [`errorHandler.js`](file:///d:/789/bff/src/middleware/errorHandler.js)

全局错误处理：生产环境隐藏内部错误详情，开发环境返回完整错误信息。

#### requestLogger

**文件**: [`requestLogger.js`](file:///d:/789/bff/src/middleware/requestLogger.js)

请求生命周期日志：进入时记录方法+路径+IP，完成时记录状态码+耗时。

---

### 5.4 代理层 (proxy)

#### proxyMapping

**文件**: [`proxyMapping.js`](file:///d:/789/bff/src/proxy/proxyMapping.js)

路由认证策略映射，与后端 `SecurityConfig.java` 保持一致：

```javascript
public: [                           // 不需要 JWT
  '/api/student/login', '/api/teacher/login', '/api/admin/login',
  '/api/course/list', '/api/course/list/simple'
]

authenticated: [                    // 需要 JWT
  '/api/auth/refresh', '/api/auth/validate',
  '/api/selection/', '/api/attendance/',
  '/api/student/', '/api/teacher/',
  '/api/admin/', '/api/score/', '/api/lab/'
]
```

`requiresAuth(path)` 方法：公开路由返回 false，认证路由返回 true，默认返回 true。

#### transparentProxy

**文件**: [`transparentProxy.js`](file:///d:/789/bff/src/proxy/transparentProxy.js)

透明代理插件，使用 Fastify 的通配路由 `/api/*` 匹配所有 API 请求：

1. **preHandler**: 根据 proxyMapping 决定是否需要 `jwtVerify`
2. **handler**: 转发请求到后端 (`config.backendUrl`)，透传请求头/请求体/状态码/响应头
3. **特殊处理**: 识别二进制响应（Excel 导出等），使用 `arrayBuffer` 处理

---

### 5.5 服务 (services)

#### backendClient

**文件**: [`backendClient.js`](file:///d:/789/bff/src/services/backendClient.js)

封装对 Spring Boot 后端的 HTTP 调用：

| 方法 | 说明 |
|------|------|
| `request(method, path, options)` | 通用请求方法，返回 JSON 或原始响应 |
| `get(path, options)` | GET 请求 |
| `post(path, body, options)` | POST 请求 |
| `put(path, body, options)` | PUT 请求 |
| `delete(path, options)` | DELETE 请求 |

支持 JSON 和非 JSON 响应，记录请求耗时和错误日志。

---

### 5.6 工具 (utils)

#### logger.js

**文件**: [`logger.js`](file:///d:/789/bff/src/utils/logger.js)

```javascript
createLogger(module)  // 返回 { info, warn, error, debug }
maskSensitive(obj, keys)  // 脱敏指定字段（password, token, secret 等）
```

日志格式: `[ISO时间戳] [BFF:模块] [级别] 消息 | key=value | ...`

---

## 6. 前端模块 (Vue 3)

### 6.1 入口与配置

#### main.js

**文件**: [`main.js`](file:///d:/789/frontend/src/main.js)

创建 Vue 应用，全局注册 Element Plus 和 Router，挂载到 `#app`。

#### vite.config.js

**文件**: [`vite.config.js`](file:///d:/789/frontend/vite.config.js)

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 开发端口 | 3000 | 前端开发服务器 |
| 代理目标 (BFF) | `http://localhost:4000` | BFF 模式默认 |
| 代理目标 (直连) | `http://localhost:8080` | 降级模式 |
| 路径别名 `@` | `src/` | 模块导入快捷方式 |

环境变量 `VITE_BFF_ENABLED` 控制代理目标。

#### App.vue

**文件**: [`App.vue`](file:///d:/789/frontend/src/App.vue)

根组件，包含 `<router-view />` 和全局样式。

---

### 6.2 API 封装层 (api)

每个 API 模块对应一个后端 Controller，共 9 个文件：

| API 模块 | 文件 | 对应后端 Controller |
|----------|------|---------------------|
| auth | [`auth.js`](file:///d:/789/frontend/src/api/auth.js) | AuthController |
| admin | [`admin.js`](file:///d:/789/frontend/src/api/admin.js) | AdminController |
| student | [`student.js`](file:///d:/789/frontend/src/api/student.js) | StudentController |
| teacher | [`teacher.js`](file:///d:/789/frontend/src/api/teacher.js) | TeacherController |
| course | [`course.js`](file:///d:/789/frontend/src/api/course.js) | CourseController |
| lab | [`lab.js`](file:///d:/789/frontend/src/api/lab.js) | LabController |
| selection | [`selection.js`](file:///d:/789/frontend/src/api/selection.js) | SelectionController |
| score | [`score.js`](file:///d:/789/frontend/src/api/score.js) | ScoreController |
| attendance | [`attendance.js`](file:///d:/789/frontend/src/api/attendance.js) | AttendanceController |

所有 API 模块统一通过 `axios` 实例 (来自 `utils/request.js`) 发起请求。

---

### 6.3 路由 (router)

**文件**: [`index.js`](file:///d:/789/frontend/src/router/index.js)

#### 路由表

| 路径 | 组件 | 角色 |
|------|------|------|
| `/login` | Login.vue | 公开 |
| `/student/*` | StudentLayout.vue | student |
| `/teacher/*` | TeacherLayout.vue | teacher |
| `/admin/*` | AdminLayout.vue | admin |

#### 导航守卫

`beforeEach` 守卫实现：
- 无 Token → 重定向到 `/login`
- Token 存在但角色不匹配 → 重定向到对应首页
- 已登录访问 `/login` → 重定向到对应首页

#### 嵌套路由

| 父路由 | 子路径 | 子组件 |
|--------|--------|--------|
| `/student` | `course` | StudentCourse.vue |
| `/student` | `my-course` | StudentMyCourse.vue |
| `/student` | `schedule` | StudentSchedule.vue |
| `/student` | `attendance` | StudentAttendance.vue |
| `/student` | `attendance-history` | StudentAttendanceHistory.vue |
| `/teacher` | `course` | TeacherCourse.vue |
| `/teacher` | `students/:courseId` | TeacherStudentList.vue |
| `/teacher` | `attendance` | TeacherAttendance.vue |
| `/teacher` | `score` | TeacherScore.vue |
| `/admin` | `students` | AdminStudent.vue |
| `/admin` | `teachers` | AdminTeacher.vue |
| `/admin` | `courses` | AdminCourse.vue |
| `/admin` | `labs` | AdminLab.vue |

---

### 6.4 工具模块 (utils)

#### request.js — HTTP 客户端封装

**文件**: [`request.js`](file:///d:/789/frontend/src/utils/request.js)

| 功能 | 说明 |
|------|------|
| 基础配置 | baseURL: `/api`, timeout: 10s, withCredentials: true |
| BFF 模式 | `VITE_BFF_ENABLED !== 'false'` → Cookie 自动携带 |
| 降级模式 | 手动管理 Token，过期自动刷新 |
| 请求拦截器 | 附加 Authorization 头 (降级模式), Token 快过期时自动刷新 |
| 响应拦截器 | 统一错误处理: 401→重新登录, 403→权限不足, 423→账号锁定 |
| 请求队列 | Token 刷新期间挂起的请求排队，刷新完成后统一重发 |

#### tokenManager.js — Token 生命周期管理

**文件**: [`tokenManager.js`](file:///d:/789/frontend/src/utils/tokenManager.js)

| 功能 | BFF 模式 | 降级模式 |
|------|----------|----------|
| Token 存储 | Cookie (HttpOnly) | localStorage |
| Token 读取 | 返回 null (由 Cookie 携带) | 从 localStorage 读取 |
| Token 刷新 | `fetch /api/auth/refresh` 静默刷新 | Axios 请求并存储新 Token |
| 过期检测 | 返回 false (由 BFF 管理) | 比较时间戳 |
| 快过期阈值 | — | 10 分钟 |

#### offlineCheckin.js — 离线签到队列

**文件**: [`offlineCheckin.js`](file:///d:/789/frontend/src/utils/offlineCheckin.js)

| 方法 | 说明 |
|------|------|
| `enqueueCheckIn(studentId, courseId)` | 加入离线队列（自动去重） |
| `getQueue()` | 获取队列 |
| `removeFromQueue(studentId, courseId)` | 从队列移除 |
| `clearQueue()` | 清空队列 |
| `getQueueSize()` | 获取队列大小 |

存储位置: localStorage (`offline_checkin_queue`)

#### scheduleParser.js — 课表解析与冲突检测

**文件**: [`scheduleParser.js`](file:///d:/789/frontend/src/utils/scheduleParser.js)

| 方法 | 说明 |
|------|------|
| `parseCourseTime(courseTime)` | 解析 "周一 1-2节" → `{day, dayName, periods, startPeriod, endPeriod, time}` |
| `detectConflict(timeA, timeB)` | 检测两个课程时间是否冲突 |
| `detectConflictWithList(newCourseTime, existingCourses)` | 检测新课程是否与已有课程冲突 |
| `buildScheduleGrid(courses)` | 构建 7×5 课表网格数据 |

内置节次映射: 1-2节(08:00-09:40), 3-4节(10:00-11:40), 5-6节(14:00-15:40), 7-8节(16:00-17:40), 9-10节(19:00-20:40)

#### scheduleEventBus.js / scheduleCache.js

用于课表数据的跨组件通信和缓存管理。

#### passwordValidator.js

**文件**: [`passwordValidator.js`](file:///d:/789/frontend/src/utils/passwordValidator.js)

密码强度校验规则：
- 长度 8-20 字符
- 不能包含空格
- 必须包含大小写字母、数字、特殊符号中至少**三种**

---

### 6.5 视图组件 (views)

#### 登录页

**文件**: [`Login.vue`](file:///d:/789/frontend/src/views/Login.vue)

角色选择（学生/教师/管理员）→ 表单输入 → 表单验证 → 登录请求 → 存储用户信息 → 跳转角色首页。

#### 布局组件

| 组件 | 角色 | 侧边栏菜单 |
|------|------|-----------|
| `StudentLayout.vue` | 学生 | 课程列表, 我的课程, 我的课表, 考勤签到, 签到记录 |
| `TeacherLayout.vue` | 教师 | 我的课程, 考勤管理, 成绩管理 |
| `AdminLayout.vue` | 管理员 | 学生管理, 教师管理, 课程管理, 实验室管理 |

#### 学生端视图 (6 个)

| 组件 | 功能 |
|------|------|
| `StudentCourse.vue` | 浏览所有课程、选课（冲突检测）、退课 |
| `StudentMyCourse.vue` | 查看已选课程、退课操作 |
| `StudentSchedule.vue` | 周课表视图、跨 Tab 实时刷新 |
| `StudentAttendance.vue` | 实时签到、自动状态判定、离线队列支持 |
| `StudentAttendanceHistory.vue` | 签到历史记录展示 |

#### 教师端视图 (5 个)

| 组件 | 功能 |
|------|------|
| `TeacherCourse.vue` | 查看授课课程列表 |
| `TeacherStudentList.vue` | 查看某课程选课学生名单 |
| `TeacherAttendance.vue` | 按日期查看考勤、缺勤→请假修改 |
| `TeacherScore.vue` | 录入/修改成绩、成绩导出 |

#### 管理员端视图 (5 个)

| 组件 | 功能 |
|------|------|
| `AdminStudent.vue` | 学生增删改查、密码强度校验 |
| `AdminTeacher.vue` | 教师增删改查 |
| `AdminCourse.vue` | 课程增删改查、关联教师和实验室 |
| `AdminLab.vue` | 实验室增删改查 |

---

## 7. 数据库设计

### 7.1 ER 图

```
┌──────────┐       ┌──────────┐       ┌──────────┐
│  Student  │──1:N──│ Selection │──N:1──│  Course  │──N:1──│  Teacher  │
│  (学生)   │       │  (选课)   │       │  (课程)   │       │  (教师)   │
└──────────┘       └──────────┘       └──────────┘       └──────────┘
      │                                      │
      ├───────────1:N──┐          ┌─────────┤
      │                │          │         │
┌──────────┐    ┌──────────┐    │    ┌──────────┐
│  Score   │    │Attendance│    │    │   Lab    │
│  (成绩)   │    │  (考勤)   │    │    │ (实验室)  │
└──────────┘    └──────────┘    │    └──────────┘
                                │
                        ┌──────────┐
                        │  Admin   │
                        │ (管理员)  │
                        └──────────┘
```

### 7.2 表结构详细说明

**文件**: [`init_database.sql`](file:///d:/789/database/init_database.sql)

#### student (学生表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| student_no | VARCHAR(20) UNIQUE | 非空唯一 | 学号 |
| name | VARCHAR(50) | 非空 | 姓名 |
| gender | VARCHAR(10) | — | 性别 |
| major | VARCHAR(100) | — | 专业 |
| password | VARCHAR(100) | 非空 | 密码 (BCrypt) |

#### teacher (教师表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| teacher_no | VARCHAR(20) UNIQUE | 非空唯一 | 工号 |
| name | VARCHAR(50) | 非空 | 姓名 |
| title | VARCHAR(50) | — | 职称 |
| password | VARCHAR(100) | 非空 | 密码 (BCrypt) |

#### admin (管理员表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| username | VARCHAR(50) UNIQUE | 非空唯一 | 用户名 |
| password | VARCHAR(100) | 非空 | 密码 (BCrypt) |

#### lab (实验室表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| lab_name | VARCHAR(100) | 非空 | 实验室名称 |
| location | VARCHAR(200) | — | 地点 |
| capacity | INT | — | 容量 |

#### course (课程表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| course_name | VARCHAR(100) | 非空 | 课程名 |
| teacher_id | BIGINT FK→teacher(id) | 非空 | 授课教师 |
| lab_id | BIGINT FK→lab(id) | — | 上课实验室 |
| course_time | VARCHAR(100) | — | 上课时间 (如 "周一 1-2节") |
| max_count | INT | 默认30 | 最大选课人数 |

#### selection (选课表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| student_id | BIGINT FK→student(id) | 非空 | 学生ID |
| course_id | BIGINT FK→course(id) | 非空 | 课程ID |
| select_time | DATETIME | 默认当前时间 | 选课时间 |
| UNIQUE KEY | (student_id, course_id) | 唯一约束 | 同一学生不能重复选课 |

#### score (成绩表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| student_id | BIGINT FK→student(id) | 非空 | 学生ID |
| course_id | BIGINT FK→course(id) | 非空 | 课程ID |
| score | DECIMAL(5,2) | — | 成绩 |
| UNIQUE KEY | (student_id, course_id) | 唯一约束 | 同学生同课程只有一个成绩 |

#### attendance (考勤表)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 | 自增ID |
| student_id | BIGINT FK→student(id) | 非空 | 学生ID |
| course_id | BIGINT FK→course(id) | 非空 | 课程ID |
| attendance_status | ENUM('出勤','请假','缺勤','迟到') | — | 考勤状态 |
| attendance_date | DATE | — | 考勤日期 |
| modified_by | BIGINT | — | 修改人ID (教师) |
| modify_time | DATETIME | — | 修改时间 |
| modify_reason | VARCHAR(200) | — | 修改原因 |
| UNIQUE KEY | (student_id, course_id, attendance_date) | 唯一约束 | 同学生同课程同天只能签到一次 |

#### 索引

```sql
-- 查询优化索引
idx_course_teacher  ON course(teacher_id)
idx_course_lab      ON course(lab_id)
idx_selection_student ON selection(student_id)
idx_selection_course   ON selection(course_id)
idx_score_student      ON score(student_id)
idx_score_course       ON score(course_id)
idx_attendance_student ON attendance(student_id)
idx_attendance_course  ON attendance(course_id)
```

#### 种子数据

初始化脚本包含：1 个管理员、3 位教师、5 名学生、5 间实验室、5 门课程。

---

## 8. 安全体系

### 8.1 认证流程

```
用户登录 → 输入账号密码
  → 后端验证密码 (BCrypt匹配)
    ├─ 成功 → 生成 JWT Token → BFF签发 HttpOnly Cookie → 返回用户信息
    └─ 失败 → LoginAttemptService 记录失败
      ├─ <5次 → 返回错误 + 剩余次数
      └─ ≥5次(15分钟内) → 锁定30分钟 → 返回 HTTP 423
```

### 8.2 请求认证

```
每个请求:
  → BFF层: Cookie提取Token → jwt.verify() → 附加Authorization头
  → 后端: JwtFilter提取Token → 设置SecurityContext
  → SecurityConfig: 路由级RBAC鉴权
```

### 8.3 安全特性清单

| 特性 | 实现方式 |
|------|----------|
| 密码存储 | BCrypt 不可逆哈希 |
| 密码迁移 | `PasswordMigration` 自动升级明文密码 |
| 登录锁定 | `LoginAttemptService` 内存计数 |
| JWT 签名 | HS256 + 密钥 |
| Token 有效期 | 24 小时可配置 |
| Token 存储 | HttpOnly Cookie (BFF模式) / localStorage (降级) |
| CSRF 防护 | 前后端分离 + 无状态会话 |
| CORS | 仅允许 `localhost:3000` 和 `localhost:4000` |
| 参数校验 | `@Valid` + Spring Validation |
| 全局异常 | 统一错误响应格式 |
| RBAC | `student` / `teacher` / `admin` 三级角色 |

---

## 9. CI/CD 持续集成

**文件**: [`attendance-ci.yml`](file:///d:/789/.github/workflows/attendance-ci.yml)

### 触发条件

- `push` 到 `main` 分支（仅 `backend/src/**`、CI 配置、数据库脚本变更时）
- `pull_request` 到 `main` 分支
- 手动触发 (`workflow_dispatch`)

### 流水线阶段

```
1. Backend JUnit Tests
   ├─ MySQL 8.0 服务容器
   ├─ JDK 17 (Temurin)
   ├─ 执行 init_database.sql
   ├─ mvn verify (73 测试用例)
   ├─ 发布 JUnit 测试报告
   └─ 上传测试产物

2. API Integration Tests
   ├─ 依赖 Backend JUnit Tests
   ├─ MySQL 8.0 服务容器
   └─ mvn verify

3. Notify on Failure (仅失败时)
   └─ 生成失败摘要报告
```

### 测试覆盖

| 测试类 | 用例数 | 覆盖内容 |
|--------|--------|---------|
| `AttendanceServiceTest` | 30 | 签到流程、考勤查询、状态修改、离线队列、数据一致性 |
| `OfflineQueueRecoveryTest` | 16 | 离线队列恢复、数据损坏处理、并发访问、重试 |
| `PasswordEncoderTest` | 10 | 密码编码、匹配验证、边界值 |
| `JwtUtilTest` | 9 | Token 生成、解析、过期验证 |
| `SecurityIntegrationTest` | 8 | 登录认证、权限控制、Token 安全 |
| **总计** | **73** | |

---

## 10. 项目运行方式

### 10.1 环境要求

| 环境 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Node.js | 18+ |
| MySQL | 8.0 |
| Maven | 3.6+ |
| npm | 9+ |

### 10.2 数据库初始化

```bash
mysql -u root -p < d:/789/database/init_database.sql
```

这将自动创建数据库 `lab_course_system`、8 张数据表、索引和种子数据。

### 10.3 后端启动

```bash
cd d:/789/backend

# 开发模式
mvn spring-boot:run

# 生产模式
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# 打包运行
mvn clean package -DskipTests
java -jar target/lab-course-system-1.0.0.jar
```

后端运行在 `http://localhost:8080`

### 10.4 BFF 中间层启动 (可选)

```bash
cd d:/789/bff
npm install
npm run dev     # 开发模式（热重载）
npm start       # 生产模式
```

BFF 运行在 `http://localhost:4000`

### 10.5 前端启动

```bash
cd d:/789/frontend
npm install
npm run dev
```

前端运行在 `http://localhost:3000`

### 10.6 降级模式 (不使用 BFF)

修改 `frontend/.env.development`:

```
VITE_BFF_ENABLED=false
```

此时前端直接代理到 `http://localhost:8080`。

### 10.7 运行测试

```bash
# 后端全部测试
mvn verify -f backend/pom.xml

# 指定测试类
mvn test -f backend/pom.xml -Dtest=AttendanceServiceTest

# BFF 测试
cd bff && npm test

# 前端测试
cd frontend && npm test
```

### 10.8 默认账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |
| 教师 | T001 / T002 / T003 | 123456 |
| 学生 | S001 ~ S005 | 123456 |

---

## 11. 依赖关系图

### 11.1 后端 Maven 依赖

```
lab-course-system (Spring Boot 3.2.0)
├── spring-boot-starter-web          # Web MVC
├── spring-boot-starter-data-jpa     # JPA + Hibernate
├── spring-boot-starter-security     # Spring Security
├── spring-boot-starter-validation   # Bean Validation
├── mysql-connector-j                # MySQL 驱动
├── jjwt-api / jjwt-impl / jjwt-jackson  # JWT (0.12.3)
├── lombok                           # 代码简化
├── spring-boot-starter-test         # 测试 (JUnit 5)
└── spring-security-test             # 安全测试
```

### 11.2 BFF npm 依赖

```
lab-course-bff (Fastify 4.28.0)
├── @fastify/cookie                  # Cookie 处理
├── @fastify/cors                    # CORS
├── @fastify/formbody                # 表单解析
├── jsonwebtoken                     # JWT 签发/验证
├── pino + pino-pretty              # 日志
├── zod                              # 参数校验
└── vitest (dev)                     # 测试
```

### 11.3 前端 npm 依赖

```
lab-course-frontend (Vue 3.4)
├── vue + vue-router                 # 核心框架
├── vite + @vitejs/plugin-vue        # 构建工具
├── axios                            # HTTP 客户端
├── element-plus                     # UI 组件库
├── xlsx                             # Excel 导出
└── vitest (dev)                     # 测试
```

### 11.4 组件调用关系图

```
Vue 视图组件
  ↓ (import)
API 封装层 (api/*.js)
  ↓ (import)
request.js (Axios 实例)
  ↓ (import)
tokenManager.js
  ↓ (HTTP)
BFF (Fastify) 或 直接到 Spring Boot
  ↓
Spring Boot Controller
  ↓
Service 实现
  ↓
JPA Repository
  ↓
MySQL 数据库
```

---

> **文档生成**: 基于项目源码自动分析生成 | **工具**: TRAE IDE Code Analysis Engine