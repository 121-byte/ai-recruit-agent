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
    sourcemap: false,
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('node_modules/vue') || id.includes('node_modules/@vue')) {
            return 'vendor-vue'
          }
          if (id.includes('node_modules/ant-design-vue')) {
            return 'vendor-antd'
          }
          if (id.includes('node_modules/@ant-design/icons-vue')) {
            return 'vendor-icons'
          }
          if (id.includes('node_modules/axios') || id.includes('node_modules/dayjs') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) {
            return 'vendor-core'
          }
          return 'vendor'
        }
      }
    }
  }
})
