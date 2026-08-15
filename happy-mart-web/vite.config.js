import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8074',
        changeOrigin: true
      },
      // 本地商品图片（v1.10）：前端 <img src="/upload/..."> 开发时经 Vite 代理到后端 8074
      // 生产环境由 Nginx location ^~ /upload/ 反代，两套链路一致
      '/upload': {
        target: 'http://localhost:8074',
        changeOrigin: true
      }
    }
  }
})
