/**
 * 路由认证策略映射
 * 基于 Spring Security 安全配置定义哪些路径需要认证
 *
 * 来源: backend/.../config/SecurityConfig.java
 */
// ── 公共路由（不需要 JWT）──
const publicPaths = [
  '/api/student/login',
  '/api/teacher/login',
  '/api/admin/login',
  '/api/course/list',
  '/api/course/list/simple',
]

// ── 需要认证的路由 ──
const authenticatedPaths = [
  '/api/auth/refresh',
  '/api/auth/validate',
  '/api/selection/',
  '/api/attendance/',
  '/api/student/',
  '/api/teacher/',
  '/api/admin/',
  '/api/score/',
  '/api/lab/',
]

/**
 * 路由认证策略映射
 * 基于 Spring Security 安全配置定义哪些路径需要认证
 *
 * 来源: backend/.../config/SecurityConfig.java
 */
export const proxyMapping = {
  public: publicPaths,
  authenticated: authenticatedPaths,

  /**
   * 判断路径是否需要 JWT 认证
   */
  requiresAuth(path) {
    if (publicPaths.some(p => path === p)) return false
    if (authenticatedPaths.some(p => path.startsWith(p))) return true
    return true // 默认需要认证
  },
}