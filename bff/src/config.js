const env = process.env

export const config = {
  port: parseInt(env.BFF_PORT || '4000', 10),
  backendUrl: env.BACKEND_URL || 'http://localhost:8080',
  nodeEnv: env.NODE_ENV || 'development',

  jwt: {
    secret: env.JWT_SECRET,
    expiration: parseInt(env.JWT_EXPIRATION || '86400000', 10),
    // Security fix (HIGH-001): 双Token配置
    accessTokenCookieName: 'bff_access_token',
    accessTokenMaxAge: 30 * 60 * 1000,         // 30分钟
    refreshTokenCookieName: 'bff_refresh_token',
    refreshTokenMaxAge: 7 * 24 * 60 * 60 * 1000, // 7天
    cookieName: 'bff_token',                   // 兼容旧版
    cookieMaxAge: 24 * 60 * 60 * 1000,         // 兼容旧版
  },

  log: {
    level: env.LOG_LEVEL || 'info',
    pretty: env.NODE_ENV !== 'production',
  },
}