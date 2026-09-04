import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const moduleId = id.replaceAll('\\', '/')
          if (!moduleId.includes('/node_modules/')) return undefined
          if (moduleId.includes('/zrender/')) return 'zrender'
          if (moduleId.includes('/echarts/')) return 'echarts'
          if (moduleId.includes('/lodash-es/')) return 'lodash-es'
          if (moduleId.includes('/dayjs/')) return 'dayjs'
          if (moduleId.includes('/element-plus/')) return 'element-plus'
          if (moduleId.includes('/@vue/') || moduleId.includes('/vue/') || moduleId.includes('/vue-router/') || moduleId.includes('/pinia/')) return 'vue-stack'
          if (moduleId.includes('/axios/')) return 'axios'
          return 'vendor'
        }
      }
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true
      }
    }
  }
})
