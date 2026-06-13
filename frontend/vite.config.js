import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const bffEnabled = env.VITE_BFF_ENABLED !== 'false'
  const proxyTarget = bffEnabled
    ? 'http://localhost:4000'
    : 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          rewrite: (p) => p
        }
      }
    },
    test: {
      environment: 'jsdom',
      globals: true,
    }
  }
})