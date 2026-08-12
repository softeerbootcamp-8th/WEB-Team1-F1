// vitest 설정 키(test)를 함께 쓰려면 vite 가 아니라 vitest 의 defineConfig 를 써야 타입이 맞는다
import { defineConfig } from 'vitest/config'
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
  test: {
    // 훅과 컴포넌트를 실제로 렌더해야 타이머와 이펙트를 관찰할 수 있다, 순수 함수만 있을 때는 node 로 충분했다
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
  server: {
    // 실행 환경이 PORT 를 지정하면 그 포트를 사용 (없으면 vite 기본 5173)
    port: process.env.PORT ? Number(process.env.PORT) : undefined,
  },
})
