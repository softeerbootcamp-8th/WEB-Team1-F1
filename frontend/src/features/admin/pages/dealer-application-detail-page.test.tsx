import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  approveDealerApplication,
  fetchDealerApplicationDetail,
  rejectDealerApplication,
} from '../api'
import type { DealerApplicationDetail, DealerApplicationStatus } from '../types'
import { DealerApplicationDetailPage } from './dealer-application-detail-page'

vi.mock('../api', () => ({
  fetchDealerApplicationDetail: vi.fn(),
  approveDealerApplication: vi.fn(),
  rejectDealerApplication: vi.fn(),
}))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

const fetchMock = vi.mocked(fetchDealerApplicationDetail)
const approveMock = vi.mocked(approveDealerApplication)
const rejectMock = vi.mocked(rejectDealerApplication)

function detail(status: DealerApplicationStatus = 'PENDING'): DealerApplicationDetail {
  return {
    id: 1,
    applicantId: 42,
    username: 'applicant1',
    realName: '박신청',
    email: 'a1@race.kr',
    phone: '01011112222',
    status,
    rejectReason: status === 'REJECTED' ? '사원증 사진이 흐립니다.' : null,
    appliedAt: '2026-08-16T15:04:05',
    licenseViewUrl: 'https://s3.example/signed',
    licenseContentType: 'image/jpeg',
    licenseViewExpiresAt: '2026-08-16T15:19:05',
  }
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/dealer-applications/1']}>
        <Routes>
          <Route
            path="/admin/dealer-applications/:applicationId"
            element={<DealerApplicationDetailPage />}
          />
          <Route path="/admin" element={<div>운영 관리 목록</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('딜러 심사 상세', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('신청자 정보와 사원증을 함께 보여준다', async () => {
    fetchMock.mockResolvedValue(detail())

    renderPage()

    await waitFor(() => expect(screen.getByText('applicant1')).toBeTruthy())
    expect(screen.getByText('01011112222')).toBeTruthy()
    expect(screen.getByRole('img', { name: /사원증/ }).getAttribute('src')).toBe(
      'https://s3.example/signed',
    )
  })

  // 업로드가 PDF도 받는다. img로 그리면 아무것도 보이지 않아 심사 자체가 불가능해진다
  it('PDF 사원증은 이미지가 아니라 PDF 뷰어로 그린다', async () => {
    fetchMock.mockResolvedValue({ ...detail(), licenseContentType: 'application/pdf' })

    renderPage()

    await waitFor(() => expect(screen.getByText('applicant1')).toBeTruthy())
    expect(screen.queryByRole('img', { name: /사원증/ })).toBeNull()
    expect(document.querySelector('object[type="application/pdf"]')?.getAttribute('data')).toBe(
      'https://s3.example/signed',
    )
  })

  // PDF는 object가 렌더에 실패해도 onError를 주지 않는다. 원본을 여는 길이 늘 있어야 한다
  it('형식과 무관하게 새 탭으로 원본을 열 수 있다', async () => {
    fetchMock.mockResolvedValue({ ...detail(), licenseContentType: 'application/pdf' })

    renderPage()

    const link = await screen.findByRole('link', { name: /새 탭에서 원본 보기/ })
    expect(link.getAttribute('href')).toBe('https://s3.example/signed')
  })

  it('승인하면 서버에 승인을 보낸다', async () => {
    fetchMock.mockResolvedValue(detail())
    approveMock.mockResolvedValue({ id: 1, status: 'APPROVED', rejectReason: null })

    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: '승인' })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: '승인' }))

    await waitFor(() => expect(approveMock).toHaveBeenCalledWith(1))
  })

  // 사유 없는 반려는 신청자에게 아무것도 알려주지 못한다. 서버도 400으로 막지만 여기서 먼저 막는다
  it('사유 없이 반려하면 서버로 보내지 않는다', async () => {
    fetchMock.mockResolvedValue(detail())

    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: '반려' })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: '반려' }))
    fireEvent.click(await screen.findByRole('button', { name: '반려하기' }))

    await waitFor(() => expect(rejectMock).not.toHaveBeenCalled())
  })

  it('사유를 적어 반려하면 그대로 전달한다', async () => {
    fetchMock.mockResolvedValue(detail())
    rejectMock.mockResolvedValue({ id: 1, status: 'REJECTED', rejectReason: '흐림' })

    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: '반려' })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: '반려' }))
    fireEvent.change(await screen.findByLabelText('반려 사유'), {
      target: { value: '사원증 사진이 흐립니다.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '반려하기' }))

    await waitFor(() =>
      expect(rejectMock).toHaveBeenCalledWith(1, '사원증 사진이 흐립니다.'),
    )
  })

  // 판정은 되돌릴 수 없다. 버튼이 남아 있으면 눌러 놓고 409를 받는다
  it('이미 판정된 신청에는 판정 버튼을 두지 않는다', async () => {
    fetchMock.mockResolvedValue(detail('REJECTED'))

    renderPage()

    await waitFor(() => expect(screen.getByText('사원증 사진이 흐립니다.')).toBeTruthy())
    expect(screen.queryByRole('button', { name: '승인' })).toBeNull()
    expect(screen.queryByRole('button', { name: '반려' })).toBeNull()
  })

  it('상세를 못 읽으면 목록으로 돌아갈 길을 준다', async () => {
    fetchMock.mockRejectedValue(new Error('boom'))

    renderPage()

    await waitFor(() =>
      expect(screen.getByText('신청 상세를 불러오지 못했습니다')).toBeTruthy(),
    )
    expect(screen.getByRole('link', { name: '운영 관리로' })).toBeTruthy()
  })
})
