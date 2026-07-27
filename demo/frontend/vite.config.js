import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  base: '/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      // 后端 8888，API 前缀 /api，含 SSE 流式接口
      '/api': {
        target: 'http://localhost:8888',
        changeOrigin: true,
        // SSE 长连接需要禁用超时与缓冲
        ws: false,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            // 保持长连接，不缓存
            proxyReq.setHeader('Connection', 'keep-alive')
          })
        }
      }
    }
  },
  build: {
    target: 'es2020',
    outDir: 'dist',
    sourcemap: false
  }
})
