import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchDealerApplications } from '../api'
import type { DealerApplicationSummary } from '../types'
import { AdminHomePage } from './admin-home-page'

vi.mock('../api', () => ({ fetchDealerApplications: vi.fn() }))
vi.mock('@/features/auth/auth-context', () => ({
  useAuth: () => ({ user: { realName: '관리자' } }),
}))

const fetchMock = vi.mocked(fetchDealerApplications)

function application(id: number, realName: string): DealerApplicationSummary {
  return {
    id,
    applicantId: id + 100,
    username: `applicant${id}`,
    realName,
    status: 'PENDING',
    appliedAt: '2026-08-16T15:04:05',
  }
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin']}>
        <AdminHomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('관리자 운영 홈', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('기본으로 심사 대기 목록을 읽는다', async () => {
    fetchMock.mockResolvedValue({ applications: [application(1, '박신청')] })

    renderPage()

    await waitFor(() => expect(screen.getByText('박신청')).toBeTruthy())
    expect(fetchMock).toHaveBeenCalledWith('PENDING')
  })

  // 상태가 캐시 키에 들어가지 않으면 탭을 옮긴 첫 순간에 이전 상태의 목록이 그대로 보인다
  it('탭을 옮기면 그 상태로 다시 읽는다', async () => {
    fetchMock.mockResolvedValue({ applications: [] })

    renderPage()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('PENDING'))

    fireEvent.mouseDown(screen.getByRole('tab', { name: '반려' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('REJECTED'))
  })

  it('신청이 없으면 비어 있음을 알린다', async () => {
    fetchMock.mockResolvedValue({ applications: [] })

    renderPage()

    await waitFor(() =>
      expect(screen.getByText('심사 대기 상태의 신청이 없습니다')).toBeTruthy(),
    )
  })

  it('목록을 못 읽으면 실패를 알린다', async () => {
    fetchMock.mockRejectedValue(new Error('boom'))

    renderPage()

    await waitFor(() =>
      expect(screen.getByText('신청 목록을 불러오지 못했습니다')).toBeTruthy(),
    )
  })

  // 목록에 사원증 주소가 실리면 관리자가 열어 보지도 않은 서명이 건수만큼 발급된다
  it('목록 항목은 상세로 가는 링크다', async () => {
    fetchMock.mockResolvedValue({ applications: [application(7, '박신청')] })

    renderPage()

    await waitFor(() => expect(screen.getByText('박신청')).toBeTruthy())
    expect(screen.getByRole('link').getAttribute('href')).toBe(
      '/admin/dealer-applications/7',
    )
  })
})
