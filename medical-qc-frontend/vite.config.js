// vite.config.js
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 规范化 Windows 扩展路径，避免 Codex/PowerShell 传入的 `\\?\` 前缀
 * 让 Vite 在依赖预构建阶段生成包含 `?` 的非法目录。
 *
 * @param {string} rawPath 原始文件路径
 * @returns {string} 兼容 Vite 的标准路径
 */
const normalizeWindowsFsPath = (rawPath) => {
  if (typeof rawPath !== 'string' || rawPath.length === 0) {
    return rawPath
  }

  if (rawPath.startsWith('\\\\?\\')) {
    return rawPath.slice(4)
  }

  if (rawPath.startsWith('//?/')) {
    return rawPath.slice(4)
  }

  return rawPath
}

const projectRoot = normalizeWindowsFsPath(fileURLToPath(new URL('.', import.meta.url)))

export default defineConfig({
  root: projectRoot,
  cacheDir: path.join(projectRoot, '.vite'),
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.join(projectRoot, 'src'),
    },
  },
  // 👇 必须添加 server.proxy 配置
  server: {
    port: 5173, // 前端端口 (禁止修改)
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080', // Spring Boot 后端地址
        changeOrigin: true,
        secure: false,
      },
      '/static': {
        target: 'http://localhost:8080', // Spring Boot 后端地址
        changeOrigin: true,
        secure: false,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return
          }

          if (id.includes('@element-plus/icons-vue')) {
            return 'vendor-element-plus-icons'
          }

          if (id.includes('zrender')) {
            return 'vendor-zrender'
          }

          if (id.includes('echarts')) {
            return 'vendor-echarts'
          }

          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'vendor-element-plus'
          }

          if (id.includes('dayjs')) {
            return 'vendor-dayjs'
          }

          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
            return 'vendor-vue'
          }

          return 'vendor-misc'
        },
      },
    },
  },
})
