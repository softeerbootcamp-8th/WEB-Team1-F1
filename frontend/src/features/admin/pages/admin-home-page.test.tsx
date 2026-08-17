import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchDealerApplications, fetchUsers } from '../api'
import { AdminHomePage } from './admin-home-page'

// 두 패널이 각자 자기 목록을 읽는다. 어느 쪽이 불렸는지로 열린 탭을 판정한다
vi.mock('../api', () => ({
  fetchDealerApplications: vi.fn(),
  fetchUsers: vi.fn(),
  fetchUserDetail: vi.fn(),
  suspendUser: vi.fn(),
  activateUser: vi.fn(),
}))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/features/auth/auth-context', () => ({
  useAuth: () => ({ user: { realName: '관리자' } }),
  ROLE_LABEL: { GENERAL: '개인', DEALER: '딜러', EVALUATOR: '평가사', ADMIN: '관리자' },
}))

const applicationsMock = vi.mocked(fetchDealerApplications)
const usersMock = vi.mocked(fetchUsers)

function renderPage(initialPath = '/admin') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AdminHomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('운영 관리 탭', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    applicationsMock.mockResolvedValue({ applications: [] })
    usersMock.mockResolvedValue({ users: [], page: 0, totalPages: 0, totalUsers: 0 })
  })

  it('/admin 은 딜러 자격 심사 탭을 연다', async () => {
    renderPage('/admin')

    await waitFor(() => expect(applicationsMock).toHaveBeenCalled())
    expect(screen.getByRole('tab', { name: '딜러 자격 심사' }).getAttribute('aria-selected')).toBe(
      'true',
    )
  })

  // 탭이 곧 주소라, 이 주소를 링크로 받거나 새로고침해도 회원 관리가 열려 있어야 한다
  it('/admin/users 는 회원 관리 탭을 연다', async () => {
    renderPage('/admin/users')

    await waitFor(() => expect(usersMock).toHaveBeenCalled())
    expect(screen.getByRole('tab', { name: '회원 관리' }).getAttribute('aria-selected')).toBe(
      'true',
    )
  })

  it('탭을 누르면 그 탭의 목록을 읽는다', async () => {
    renderPage('/admin')
    await waitFor(() => expect(applicationsMock).toHaveBeenCalled())

    fireEvent.mouseDown(screen.getByRole('tab', { name: '회원 관리' }))

    await waitFor(() => expect(usersMock).toHaveBeenCalled())
  })

  /*
   * 보이지 않는 탭의 목록을 미리 읽으면 관리자가 열지도 않은 회원 목록이 매번 조회된다.
   * 그 조회는 인덱스를 타지 못하고 전체를 훑으므로 특히 헛되다
   */
  it('열려 있지 않은 탭의 목록은 읽지 않는다', async () => {
    renderPage('/admin')

    await waitFor(() => expect(applicationsMock).toHaveBeenCalled())
    expect(usersMock).not.toHaveBeenCalled()
  })
})
