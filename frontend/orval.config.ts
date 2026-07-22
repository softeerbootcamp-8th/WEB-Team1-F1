// orval.config.ts
import { defineConfig } from 'orval'

export default defineConfig({
  api: {
    input: {
      target: 'http://localhost:8080/v3/api-docs',
    },
    output: {
      target: 'src/api/generated',
      client: 'react-query',
      httpClient: 'axios',
      override: {
        mutator: {
          path: 'src/lib/axios.ts',
          name: 'customInstance', // 프로젝트 axios 인스턴스 사용
        },
      },
    },
  },
})
