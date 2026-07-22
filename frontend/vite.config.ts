import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 실행 환경이 PORT 를 지정하면 그 포트를 사용 (없으면 vite 기본 5173)
    port: process.env.PORT ? Number(process.env.PORT) : undefined,
  },
})
