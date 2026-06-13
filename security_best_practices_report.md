# 实验选课系统 — 安全最佳实践审计报告

**项目名称**: 实验选课系统 (Lab Course System)  
**技术栈**: Spring Boot 3.x (Java) + Fastify/Express (Node.js BFF) + Vue 3 (Vite) + MySQL  
**审计日期**: 2026-06-13  
**审计范围**: 全栈代码安全审计（Backend / BFF / Frontend / Database）

---

## 执行摘要

本项目是一个三层架构的实验选课管理系统，涵盖 Spring Boot 后端、Fastify BFF 中间层和 Vue 3 前端。整体代码结构规范，已实现了基本的认证授权机制（JWT + Spring Security），但仍存在 **2 个 Critical**、**8 个 High**、**6 个 Medium**、**3 个 Low** 级别的安全漏洞。最严重的问题集中在 JWT 密钥硬编码、Token 刷新无额外验证、以及前端凭据存储不安全等方面。建议优先修复 Critical 和 High 级别漏洞。

---

## 严重程度分布

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| Critical | 2 | 可直接导致系统被攻破 |
| High | 8 | 存在明确可被利用的攻击路径 |
| Medium | 6 | 安全防护不足，增加攻击面 |
| Low | 3 | 防御纵深不足或信息泄露 |

---

## Critical 级别

### [CRIT-001] JWT 密钥硬编码且跨模块共享

- **严重程度**: Critical
- **规则来源**: EXPRESS-SESS-002 / Spring Security Best Practice
- **位置**:
  - [JwtUtil.java](file:///d:/789/backend/src/main/java/com/labcourse/util/JwtUtil.java#L16-L17)
  - [config.js](file:///d:/789/bff/src/config.js#L9)
  - [application.yml](file:///d:/789/backend/src/main/resources/application.yml#L23)
  - [.env.example](file:///d:/789/bff/.env.example#L7)
- **证据**:
  ```java
  // JwtUtil.java:16-17
  @Value("${jwt.secret:lab-course-system-secret-key-2024-very-long-secret}")
  private String secret;
  ```
  ```javascript
  // config.js:9
  secret: env.JWT_SECRET || 'lab-course-system-secret-key-2024-very-long-secret',
  ```
  ```yaml
  # application.yml:23
  jwt:
    secret: ${JWT_SECRET:lab-course-system-secret-key-2024-very-long-secret}
  ```
- **影响**: JWT 默认密钥 `lab-course-system-secret-key-2024-very-long-secret` 同时出现在源代码、配置文件和环境变量示例中，并跨 Backend 和 BFF 两个模块共享。任何获得此密钥的攻击者都可以伪造任意用户的 JWT Token，以任意身份（学生/教师/管理员）登录系统，绕过所有认证和授权控制。
- **修复建议**:
  1. 立即将 JWT Secret 更换为强随机密钥（至少 256-bit），使用 `openssl rand -base64 32` 生成
  2. 仅通过环境变量注入，不在源码或配置文件中设置默认值
  3. Backend 和 BFF 如确实需要共享密钥，应通过密钥管理服务注入，而非硬编码
  4. 考虑使用非对称密钥（RS256/ES256），仅 BFF 持有私钥签发，Backend 用公钥验证

---

### [CRIT-002] 初始化数据库脚本包含明文密码

- **严重程度**: Critical
- **规则来源**: General Security Best Practice
- **位置**: [init_database.sql](file:///d:/789/database/init_database.sql#L133-L148)
- **证据**:
  ```sql
  -- init_database.sql:133-148
  INSERT INTO admin (username, password) VALUES ('admin', '123456');
  INSERT INTO teacher (teacher_no, name, title, password) VALUES
  ('T001', '张三', '教授', '123456'),
  ('T002', '李四', '副教授', '123456'),
  ('T003', '王五', '讲师', '123456');
  INSERT INTO student (student_no, name, gender, major, password) VALUES
  ('S001', '王小明', '男', '计算机科学与技术', '123456'),
  ...
  ```
- **影响**: 初始 SQL 脚本中所有账号的密码均为明文 `123456`。虽然 [PasswordMigration.java](file:///d:/789/backend/src/main/java/com/labcourse/config/PasswordMigration.java) 会在首次启动时自动将明文密码升级为 BCrypt 哈希，但该 SQL 脚本本身是源码的一部分，任何人查看仓库即可获得初始密码。此外，若 BCrypt 迁移失败或异常中断，系统将以明文密码运行。
- **修复建议**:
  1. 在 SQL 脚本中直接使用 BCrypt 哈希后的密码值
  2. 移除或注释掉 `PasswordMigration` 的自动迁移逻辑（迁移完成后应删除该组件）
  3. 使用强随机密码作为初始密码，首次登录强制修改

---

## High 级别

### [HIGH-001] JWT Token 刷新缺乏额外安全验证

- **严重程度**: High
- **规则来源**: Spring Security Best Practice / EXPRESS-AUTH-001
- **位置**:
  - [AuthController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/AuthController.java#L18-L56)
  - [auth.js](file:///d:/789/bff/src/routes/auth.js#L119-L173)
- **证据**:
  ```java
  // AuthController.java:18-56 — 接受任何非过期 Token 并生成新 Token
  @PostMapping("/refresh")
  public ResponseEntity<Map<String, Object>> refreshToken(@RequestHeader("Authorization") String authHeader) {
      ...
      String newToken = jwtUtil.generateToken(userId, username, role);
      ...
  }
  ```
  ```javascript
  // auth.js:132-141 — BFF 端同样无条件刷新
  const newToken = jwt.sign(
    { userId, username, role },
    config.jwt.secret,
    { algorithm: 'HS256', expiresIn: Math.floor(config.jwt.expiration / 1000) }
  )
  ```
- **影响**: 任何持有有效（未过期）Token 的攻击者都可以无限期刷新 Token，实现"永久登录"。即使检测到凭证泄露，也无法通过使原 Token 失效来阻止，因为攻击者可无限续期。
- **修复建议**:
  1. 引入 Refresh Token 机制：短期 Access Token (15min) + 长期 Refresh Token (7d)
  2. Refresh Token 存储在后端（数据库/Redis），支持撤销
  3. Refresh Token 使用独立的密钥签名
  4. Token 刷新时验证用户账号是否仍处于活跃状态

---

### [HIGH-002] 前端路由守卫仅依赖 localStorage，无后端强制鉴权

- **严重程度**: High
- **规则来源**: VUE-ROUTER-001
- **位置**:
  - [router/index.js](file:///d:/789/frontend/src/router/index.js#L133-L167)
  - [Login.vue](file:///d:/789/frontend/src/views/Login.vue#L140-L148)
- **证据**:
  ```javascript
  // router/index.js:133-167
  router.beforeEach((to, from, next) => {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    const isLoggedIn = user && user.token
    // 检查角色是否匹配
    const isStudentRoute = to.path.startsWith('/student') && user.role === 'student'
    ...
  })
  ```
  ```javascript
  // Login.vue:148
  localStorage.setItem('user', JSON.stringify(userData))
  ```
- **影响**: 前端路由守卫完全依赖 `localStorage` 中的数据进行权限判断。localStorage 中的数据可被用户随意修改——攻击者可以手动设置 `{role: 'admin', token: 'bff-cookie'}` 来绕过前端路由守卫，访问管理员页面。虽然实际 API 调用仍有后端权限控制，但管理员界面的结构和功能仍会暴露给攻击者。
- **修复建议**:
  1. 前端启动时调用 `/api/auth/validate` 从服务端获取真实用户身份
  2. 不在 localStorage 中存储 role、userId 等敏感身份信息（BFF 模式下 token 已是 `'bff-cookie'`，无实际验证意义）
  3. 将路由守卫视为 UX 优化，明确标注不能作为安全边界

---

### [HIGH-003] BFF 模式下登录成功仍将 Token/用户信息存储在 localStorage

- **严重程度**: High
- **规则来源**: VUE-AUTH-001 / VUE-SECRETS-001 / JS-STORAGE-001
- **位置**:
  - [Login.vue](file:///d:/789/frontend/src/views/Login.vue#L140-L148)
  - [tokenManager.js](file:///d:/789/frontend/src/utils/tokenManager.js#L35-L37)
- **证据**:
  ```javascript
  // Login.vue:141-148
  const userData = {
    ...result.data,
    role: loginForm.role,
    token: result.token || (BFF_ENABLED ? 'bff-cookie' : undefined),
    tokenExpireTime: result.data?.tokenExpireTime || (Date.now() + 86400 * 1000),
    _bffMode: BFF_ENABLED
  }
  localStorage.setItem('user', JSON.stringify(userData))
  ```
- **影响**: 虽然 BFF 模式下 Token 由 HttpOnly Cookie 管理，但登录成功后仍将用户对象（含 role、userId、name 等 PII）存入 localStorage。一旦发生 XSS 攻击，攻击者可以读取这些信息并冒用用户身份。token 值 `'bff-cookie'` 虽然是占位符，但 role 信息是真实可靠的。
- **修复建议**:
  1. BFF 模式下不要将身份信息存入 localStorage
  2. 前端通过调用 `/api/auth/validate` 获取当前用户身份
  3. 使用 sessionStorage 替代 localStorage（至少在会话结束后清除）

---

### [HIGH-004] CORS 配置允许所有请求头且携带凭据

- **严重程度**: High (Credentials + wildcard headers)
- **规则来源**: EXPRESS-CORS-001 / Spring Security CORS Best Practice
- **位置**:
  - [SecurityConfig.java](file:///d:/789/backend/src/main/java/com/labcourse/config/SecurityConfig.java#L74-L84)
  - [index.js](file:///d:/789/bff/src/index.js#L22-L25)
- **证据**:
  ```java
  // SecurityConfig.java:76-79
  configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:4000"));
  configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
  configuration.setAllowedHeaders(Arrays.asList("*")); // ← 允许所有请求头
  configuration.setAllowCredentials(true);              // ← 携带凭据
  ```
- **影响**: Spring Security 中 `allowedHeaders: *` 与 `allowCredentials: true` 的组合存在安全隐患。允许任意请求头意味着攻击者可以注入自定义请求头绕过某些安全检查。虽然 Spring 的 CORS 实现会拒绝 `*` + credentials 的无效组合（因此实际上 `*` 可能被忽略或报错），但正确的做法是明确列出所需头部。
- **修复建议**:
  1. 将 `allowedHeaders` 改为显式列表：`Authorization`, `Content-Type`, `X-Requested-With`
  2. BFF 的 Fastify CORS 配置同样建议显式指定 allowedHeaders

---

### [HIGH-005] BFF 层缺乏速率限制

- **严重程度**: High
- **规则来源**: EXPRESS-AUTH-001
- **位置**:
  - [index.js](file:///d:/789/bff/src/index.js) — 缺少 rate-limit 中间件
  - [auth.js](file:///d:/789/bff/src/routes/auth.js) — 登录路由无限制
- **证据**: Backend 有 [LoginAttemptService](file:///d:/789/backend/src/main/java/com/labcourse/service/LoginAttemptService.java) 实现内存锁机制，但 BFF 层完全没有速率限制。攻击者可绕过 BFF 直接对 Backend 发起暴力破解；或者通过 BFF 对 Backend 发起大量代理请求造成 DoS。
- **影响**: 虽然 Backend 有内存级登录锁（5次失败/15分钟窗口），但该机制基于内存 `ConcurrentHashMap`，在分布式部署或重启后丢失。BFF 层缺少速率限制意味着攻击者可无限制尝试登录，增加暴力破解成功率。
- **修复建议**:
  1. BFF 添加 `@fastify/rate-limit` 插件
  2. 登录端点设置严格限制（如 10次/分钟/IP）
  3. 考虑使用 Redis 持久化登录尝试计数（分布式友好）

---

### [HIGH-006] 数据库连接禁用 SSL

- **严重程度**: High
- **规则来源**: General Database Security Best Practice
- **位置**:
  - [application.yml](file:///d:/789/backend/src/main/resources/application.yml#L6)
  - [application-prod.yml](file:///d:/789/backend/src/main/resources/application-prod.yml#L8)
- **证据**:
  ```yaml
  # application.yml:6 和 application-prod.yml:8
  url: jdbc:mysql://localhost:3306/lab_course_system?...&useSSL=false
  ```
- **影响**: 生产环境配置中 `useSSL=false` 禁用了数据库连接加密。数据库传输的用户凭据、个人信息等将以明文在网络上传输，可被中间人攻击截获。
- **修复建议**:
  1. 生产环境启用 SSL：`useSSL=true&requireSSL=true`
  2. 配置 CA 证书和客户端证书
  3. 开发环境可保留 `useSSL=false`，但生产环境必须启用

---

### [HIGH-007] 考勤签到接口缺乏有效的 studentId 校验

- **严重程度**: High
- **规则来源**: Spring Security Authorization Best Practice
- **位置**: [AttendanceController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/AttendanceController.java#L24-L40)
- **证据**:
  ```java
  // AttendanceController.java:24-40
  @PostMapping("/check-in")
  public ResponseEntity<Map<String, Object>> checkIn(@RequestBody Map<String, Object> data) {
      Object studentIdObj = data.get("studentId");
      Long studentId = Long.valueOf(studentIdObj.toString());
      // ← 没有验证该 studentId 是否与当前登录用户一致！
      Map<String, Object> result = attendanceService.checkIn(studentId, courseId);
  }
  ```
- **影响**: 学生签到接口接受请求体中的 `studentId` 参数，但未验证该 ID 是否与当前 JWT Token 中的 userId 一致。一个已认证的学生可以通过修改 `studentId` 为其他学生签到，造成考勤数据伪造。
- **修复建议**:
  1. 从 `SecurityContextHolder` 获取当前认证用户的 userId
  2. 强制使用当前登录用户的 ID，忽略请求体中的 studentId
  3. 后端二次验证 studentId 与 JWT Token 中的 userId 是否匹配

---

### [HIGH-008] 前端登录页面在生产环境暴露测试账号凭据

- **严重程度**: High
- **规则来源**: VUE-SECRETS-001 / JS-STORAGE-001
- **位置**: [Login.vue](file:///d:/789/frontend/src/views/Login.vue#L66-L88)
- **证据**:
  ```html
  <!-- Login.vue:66-88 — 测试账号直接硬编码在页面中 -->
  <details class="test-accounts">
    <summary>测试账号</summary>
    <div class="accounts-grid">
      <div class="account-item">学生 <code>S001</code> / <code>123456</code></div>
      <div class="account-item">教师 <code>T001</code> / <code>123456</code></div>
      <div class="account-item">管理员 <code>admin</code> / <code>123456</code></div>
    </div>
  </details>
  ```
- **影响**: 测试账号和密码直接硬编码在 Login.vue 组件中。如果不做条件编译，这些凭据会随生产构建一起发布，任何用户都可以使用这些账号登录系统。
- **修复建议**:
  1. 使用 `import.meta.env.DEV` 条件渲染，仅在开发环境显示
  2. 或完全移除硬编码凭据，改为通过环境变量注入

---

## Medium 级别

### [MED-001] BFF 层缺少 Helmet 安全头

- **严重程度**: Medium
- **规则来源**: EXPRESS-HEADERS-001 / EXPRESS-FINGERPRINT-001
- **位置**: [index.js](file:///d:/789/bff/src/index.js#L11-L18)
- **证据**: Fastify 实例化时未配置任何安全头中间件，没有 CSP、X-Frame-Options、X-Content-Type-Options 等。
- **影响**: 缺少安全头使应用更容易受到 XSS、Clickjacking、MIME 嗅探等攻击。
- **修复建议**:
  1. 安装 `@fastify/helmet` 并全局注册
  2. 配置 CSP 策略，重点关注 `script-src`
  3. 添加 `X-Frame-Options: DENY` 或 CSP `frame-ancestors 'none'`

---

### [MED-002] BFF 层请求体大小无限制

- **严重程度**: Medium
- **规则来源**: EXPRESS-BODY-001
- **位置**: [index.js](file:///d:/789/bff/src/index.js#L22-L29)
- **证据**:
  ```javascript
  // index.js:29 — formbody 注册时未设置 limit
  await app.register(formbody)
  ```
  Fastify 使用 `@fastify/formbody` 解析请求体，默认无显式大小限制。
- **影响**: 攻击者可以发送超大请求体消耗服务器内存和带宽，导致 DoS。
- **修复建议**:
  1. Fastify 默认 body limit 为 1MB，但建议显式设置：`await app.register(formbody, { bodyLimit: 1048576 })`
  2. 在反向代理层（Nginx）也设置 `client_max_body_size`

---

### [MED-003] BFF 透明代理无条件转发所有请求头

- **严重程度**: Medium
- **规则来源**: EXPRESS-PROXY-001
- **位置**: [transparentProxy.js](file:///d:/789/bff/src/proxy/transparentProxy.js#L46-L55)
- **证据**:
  ```javascript
  // transparentProxy.js:46-55
  const forwardHeaders = { ...reqHeaders }
  // 移除仅 BFF 内部使用的头
  delete forwardHeaders.host
  delete forwardHeaders.connection
  // ← 其他所有客户端请求头（包括 X-Forwarded-For 等）都被转发
  ```
- **影响**: 客户端发送的自定义头（如 `X-Forwarded-For`）会被透传到后端。如果后端 Spring Boot 配置了代理信任，这可能导致 IP 欺骗。
- **修复建议**:
  1. 在转发前清理所有 `X-Forwarded-*` 头，由 BFF 重新设置
  2. 添加 `X-Forwarded-For: <真实客户端IP>`、`X-Forwarded-Proto: http` 等

---

### [MED-004] 数据库实体使用自增 ID 作为公开标识

- **严重程度**: Medium
- **规则来源**: General Security Advice - Incrementing IDs
- **位置**: 所有 Entity 类，如 [Student.java](file:///d:/789/backend/src/main/java/com/labcourse/entity/Student.java#L11-L12)
- **证据**: 所有实体使用 `@GeneratedValue(strategy = GenerationType.IDENTITY)` 自增 ID 作为主键和外键，这些 ID 通过 API 暴露给前端。
- **影响**: 攻击者可以：
  1. 通过 ID 枚举遍历资源（如遍历所有学生、课程）
  2. 推断资源数量
  3. 利用可预测的 ID 进行未授权访问（如修改 score API 中的 studentId）
- **修复建议**:
  1. 对外暴露使用 UUID 或加密 ID
  2. API 层面不直接暴露数据库自增 ID
  3. 确保所有 API 都有严格的权限校验（目前课程列表公开访问无问题，但需确保敏感接口不是仅靠 ID 来保护）

---

### [MED-005] 多个控制器缺乏输入验证

- **严重程度**: Medium
- **规则来源**: EXPRESS-INPUT-001 / Spring Input Validation Best Practice
- **位置**:
  - [ScoreController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/ScoreController.java#L19-L31)
  - [AttendanceController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/AttendanceController.java#L24-L40)
  - [SelectionController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/SelectionController.java#L21-L39)
- **证据**: 多个控制器直接使用 `Long.valueOf(data.get("xxx").toString())` 解析请求参数，未验证值是否合法（如负数、零值、超大值）。
- **影响**: 攻击者可以提交恶意参数值（如负数 studentId、超大 score 值），可能导致业务逻辑异常或数据库约束违反。
- **修复建议**:
  1. 使用 `@Valid` + JSR-303 注解（`@NotNull`, `@Positive`, `@Min`, `@Max`）
  2. 在 Service 层添加业务规则校验
  3. 使用 DTO 类替代裸 `Map<String, Object>`

---

### [MED-006] 登录成功返回完整用户对象（含敏感信息）

- **严重程度**: Medium
- **规则来源**: General API Security Best Practice
- **位置**:
  - [AdminController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/AdminController.java#L29-L37)
  - [StudentController.java](file:///d:/789/backend/src/main/java/com/labcourse/controller/StudentController.java#L30-L36)
- **证据**:
  ```java
  // AdminController.java:35 — 直接返回整个 Admin 实体
  result.put("data", admin);
  ```
- **影响**: 虽然 Student 实体使用了 `@JsonProperty(access = WRITE_ONLY)` 隐藏密码字段，但 Admin 和 Teacher 的 entity 缺少此注解。如果 entity 未来添加敏感字段（如手机号、邮箱），可能被意外泄露。
- **修复建议**:
  1. 为所有 Entity 类添加 `@JsonProperty(access = WRITE_ONLY)` 保护密码字段
  2. 登录成功返回最小化的用户信息（id, name, role），不返回完整实体

---

## Low 级别

### [LOW-001] JWT Token 过期时间过长

- **严重程度**: Low
- **规则来源**: General JWT Best Practice
- **位置**: [application.yml](file:///d:/789/backend/src/main/resources/application.yml#L24)
- **证据**: `jwt.expiration: 86400000` (24 小时)
- **影响**: 24 小时的 Token 过期时间过长，一旦 Token 泄露，攻击者有充足的时间窗口进行恶意操作。
- **修复建议**:
  1. 将 Access Token 过期时间缩短至 15-30 分钟
  2. 引入 Refresh Token 机制（见 HIGH-001）

---

### [LOW-002] JWT 日志中记录 Token 预览信息

- **严重程度**: Low
- **规则来源**: EXPRESS-ERROR-001
- **位置**: [jwtVerify.js](file:///d:/789/bff/src/middleware/jwtVerify.js#L109-L110)
- **证据**:
  ```javascript
  // jwtVerify.js:110 — JWT 验证失败时记录 Token 片段
  tokenPreview: `${token.substring(0, 10)}...${token.substring(token.length - 5)}`,
  ```
- **影响**: Token 前后缀片段被记录在日志中。虽然不完整，但增加了 Token 被猜测的风险。
- **修复建议**: 移除 Token 预览日志，仅记录 Token 是否存在

---

### [LOW-003] 日志中可能暴露 PII 数据

- **严重程度**: Low
- **规则来源**: EXPRESS-ERROR-001
- **位置**:
  - [transparentProxy.js](file:///d:/789/bff/src/proxy/transparentProxy.js#L60-L74) — 记录完整请求体
  - [requestLogger.js](file:///d:/789/bff/src/middleware/requestLogger.js) — 记录 IP 和 URL
- **证据**:
  ```javascript
  // transparentProxy.js:60-74
  const bodyStr = body ? JSON.stringify(body) : null
  const bodyPreview = bodyStr
    ? (bodyStr.length > 500 ? bodyStr.substring(0, 500) + '...(truncated)' : bodyStr)
    : '(empty)'
  ```
- **影响**: 请求体日志可能包含明文密码（登录请求）、学生个人信息等敏感数据。
- **修复建议**:
  1. 对请求体日志进行脱敏处理，过滤 password、token 等敏感字段
  2. 生产环境降低日志级别或完全禁用请求体日志

---

## 正面发现

以下安全实践值得肯定：

1. **[Student.java](file:///d:/789/backend/src/main/java/com/labcourse/entity/Student.java#L26)** — Student 实体使用 `@JsonProperty(access = WRITE_ONLY)` 防盗密码序列化泄露
2. **[LoginAttemptService.java](file:///d:/789/backend/src/main/java/com/labcourse/service/LoginAttemptService.java)** — 实现了基于内存的登录失败计数和账号锁定机制（5次失败锁定30分钟）
3. **[GlobalExceptionHandler.java](file:///d:/789/backend/src/main/java/com/labcourse/config/GlobalExceptionHandler.java#L53-L60)** — 通5异常处5理不泄露错误堆栈，返回通用错误消息
4. **[errorHandler.js](file:///d:/789/bff/src/middleware/errorHandler.js#L22-L26)** — BFF 错误处理在生产环境隐藏详细信息
5. **[jwtVerify.js](file:///d:/789/bff/src/middleware/jwtVerify.js#L54-L56)** — JWT 验证时指定了算法白名单 `algorithms: ['HS256']`
6. **[auth.js](file:///d:/789/bff/src/routes/auth.js#L49-L55)** — BFF 正确设置 HttpOnly Cookie，且 `secure` 根据环境动态设置
7. **[SecurityConfig.java](file:///d:/789/backend/src/main/java/com/labcourse/config/SecurityConfig.java)** — Spring Security 启用了方法级安全认证 `@EnableMethodSecurity`
8. **密码存储** — 使用 BCrypt 哈希存储密码，[PasswordEncoder](file:///d:/789/backend/src/main/java/com/labcourse/config/SecurityConfig.java#L31-L33) 使用默认强度

---

## 修复优先级建议

| 优先级 | 编号 | 内容 | 预计工作量 |
|--------|------|------|-----------|
| P0 | CRIT-001 | 更换 JWT 密钥，移除硬编码 | 中 |
| P0 | CRIT-002 | 数据库脚本密码哈希化 | 低 |
| P1 | HIGH-001 | JWT Token 刷新机制改造 | 高 |
| P1 | HIGH-007 | 签到接口 studentId 校验 | 低 |
| P1 | HIGH-004 | CORS 配置修复 | 低 |
| P1 | HIGH-005 | BFF 速率限制 | 中 |
| P1 | HIGH-006 | 数据库 SSL 连接 | 低 |
| P1 | HIGH-003 | 前端 localStorage 清理 | 中 |
| P1 | HIGH-002 | 前后端身份校验协调 | 中 |
| P1 | HIGH-008 | 测试账号条件渲染 | 低 |
| P2 | MED-001 | BFF 安全头配置 | 低 |
| P2 | MED-002 | 请求体大小限制 | 低 |
| P2 | MED-003 | 代理请求头清理 | 低 |
| P2 | MED-005 | 输入验证加强 | 中 |
| P2 | MED-006 | 登录响应数据最小化 | 低 |
| P3 | LOW-001~003 | 日志安全等低优先级 | 低 |

---

*报告由 security-best-practices skill 生成，基于对全栈代码的系统审计。*