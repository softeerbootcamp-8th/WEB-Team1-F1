import { QueryClient } from '@tanstack/react-query'

/** 전역 react-query 클라이언트. orval 훅이 이 클라이언트를 공유한다. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
