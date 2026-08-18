import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider, useAuth } from './auth-context'

const mocks = vi.hoisted(() => ({
  fetchMe: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  clearAuctionListCache: vi.fn(),
}))

vi.mock('./api', () => ({
  fetchMe: mocks.fetchMe,
  loginRequest: mocks.loginRequest,
  logoutRequest: mocks.logoutRequest,
}))

vi.mock('@/features/auctions/use-auction-list', () => ({
  clearAuctionListCache: mocks.clearAuctionListCache,
}))

const PREVIOUS_USER = { id: 7, realName: '김민수', role: 'GENERAL' }
const NEXT_USER = { id: 9, realName: '이서연', role: 'DEALER' }

/**
 * /login 은 로그인 상태에서도 열려 있어 로그아웃 없이 계정이 바뀔 수 있다.
 * 조회 키에 회원을 넣지 않으므로, 비우지 않으면 새 회원의 화면에 앞사람의 응답이 먼저 보인다.
 */
describe('AuthProvider 의 계정 전환', () => {
  beforeEach(() => {
    mocks.fetchMe.mockReset().mockResolvedValue(PREVIOUS_USER)
    mocks.loginRequest.mockReset().mockResolvedValue(NEXT_USER)
    mocks.logoutRequest.mockReset().mockResolvedValue(undefined)
    mocks.clearAuctionListCache.mockReset()
  })

  it('로그아웃 없이 다른 계정으로 로그인하면 앞사람의 조회 캐시를 비운다', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    )

    const { result } = renderHook(() => useAuth(), { wrapper })
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

    queryClient.setQueryData(['evaluations', 'detail', 9], { visitAddress: '앞사람 주소' })

    await act(async () => {
      await result.current.login({ username: 'seller2', password: 'password123' })
    })

    expect(queryClient.getQueryData(['evaluations', 'detail', 9])).toBeUndefined()
    expect(mocks.clearAuctionListCache).toHaveBeenCalled()
    expect(result.current.user?.id).toBe(NEXT_USER.id)
  })
})
