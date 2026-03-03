import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0', // 保留局域网访问
    port: 5173,
    open: false,
    // 新增代理配置，核心跨域解决方案
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 你的后端真实IP+端口
        changeOrigin: true, // 关键：让后端认为请求来自Vite，而非前端浏览器
        rewrite: (path) => path.replace(/^\/api/, ''), // 去掉/api前缀，后端接收到的是原接口（如/user/info）
        secure: false // 开发环境关闭https校验，避免报错
      }
    }
  }
})