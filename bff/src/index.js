import Fastify from 'fastify'
import cookie from '@fastify/cookie'
import cors from '@fastify/cors'
import formbody from '@fastify/formbody'
import helmet from '@fastify/helmet'
import rateLimit from '@fastify/rate-limit'
import { config } from './config.js'
import { setupAuthRoutes } from './routes/auth.js'
import { transparentProxyPlugin } from './proxy/transparentProxy.js'
import { errorHandler } from './middleware/errorHandler.js'
import { requestLogger } from './middleware/requestLogger.js'

async function buildApp() {
  const app = Fastify({
    logger: {
      level: config.log.level,
      transport: config.log.pretty
        ? { target: 'pino-pretty', options: { colorize: true } }
        : undefined,
    },
  })

  // 插件注册
  // Security fix (MED-001): 添加安全头
  await app.register(helmet, {
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: ["'self'", "'unsafe-inline'"],
        styleSrc: ["'self'", "'unsafe-inline'"],
        imgSrc: ["'self'", 'data:'],
        connectSrc: ["'self'"],
      },
    },
    xFrameOptions: { action: 'deny' },
  })
  await app.register(cors, {
    origin: 'http://localhost:3000',
    credentials: true,
    // Security fix (HIGH-004): 显式指定允许的请求头
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With'],
  })
  await app.register(cookie, {
    secret: config.jwt.secret,
  })
  // Security fix (MED-002): 限制请求体大小
  await app.register(formbody, { bodyLimit: 1048576 }) // 1MB

  // Security fix (HIGH-005): 全局速率限制防止暴力破解
  await app.register(rateLimit, {
    max: 100,                    // 每个IP每分钟最多100次请求
    timeWindow: '1 minute',
    keyGenerator: (request) => request.ip,
  })

  // 请求日志中间件
  app.addHook('onRequest', requestLogger)

  // 路由：认证（优先匹配，不被代理拦截）
  await setupAuthRoutes(app)

  // 透明代理：其他所有 /api/* → Spring Boot
  await app.register(transparentProxyPlugin)

  // 全局错误处理
  app.setErrorHandler(errorHandler)

  return app
}

// 启动
const start = async () => {
  const app = await buildApp()
  try {
    await app.listen({ port: config.port, host: '0.0.0.0' })
    app.log.info(`BFF server running at http://localhost:${config.port}`)
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}

// 仅在直接运行时启动（非 import 时）
if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/'))) {
  start()
}

export { buildApp }