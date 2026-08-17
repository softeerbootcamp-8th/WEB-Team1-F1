import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchMyAssignments } from '../api'
import type { EvaluationSummary } from '../types'
import { MyAssignmentsPage } from './my-assignments-page'

vi.mock('../api', () => ({
  fetchMyAssignments: vi.fn(),
}))

const fetchMock = vi.mocked(fetchMyAssignments)

function summary(overrides: Partial<EvaluationSummary> = {}): EvaluationSummary {
  return {
    evaluationId: 600,
    status: 'REQUESTED',
    assigned: true,
    plateNumber: '12가3456',
    manufacturer: 'HYUNDAI',
    model: '그랜저 IG',
    modelYear: 2021,
    visitDate: '2026-08-20',
    visitAddress: '서울 성동구 왕십리로 83',
    requestedAt: '2026-08-05T15:30:00',
    ...overrides,
  }
}

function renderPage(path = '/evaluations/my') {
  // 재시도를 끄지 않으면 실패 시나리오가 기다리기만 한다
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <MyAssignmentsPage />
      </MemoryRouter>
    </QueryClientProvider> as ReactNode,
  )
}

describe('평가사 담당 목록의 진행 중 · 완료 구분', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({ evaluations: [] })
  })

  it('기본 진입은 진행 중인 건만 읽는다', async () => {
    // 끝낸 진단이 기본 목록에 남으면 새로 나갈 건이 그 아래 묻힌다
    renderPage()

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('ACTIVE'))
  })

  it('완료 탭을 고르면 완료된 건을 따로 읽는다', async () => {
    renderPage()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('ACTIVE'))

    // Radix 탭은 mousedown에서 전환한다. click만 쏘면 아무 일도 일어나지 않는다
    fireEvent.mouseDown(screen.getByRole('tab', { name: '완료' }))

    // 승인된 건은 경매 등록 전까지 다시 제출할 수 있어, 감추는 것이 아니라 따로 열어 본다
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('COMPLETED'))
  })

  it('주소에 적힌 완료 목록으로 바로 들어온다', async () => {
    // 완료 목록에서 한 건을 확인하고 돌아왔을 때 진행 중 탭으로 되돌아가 있으면 안 된다
    renderPage('/evaluations/my?scope=COMPLETED')

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('COMPLETED'))
    expect(fetchMock).not.toHaveBeenCalledWith('ACTIVE')
  })

  it('완료된 건은 접수 시각 대신 진단을 끝낸 시각을 보여준다', async () => {
    // 완료 목록이 이 값으로 정렬되므로 보이지 않으면 순서를 읽을 수 없다
    fetchMock.mockResolvedValue({
      evaluations: [
        summary({ status: 'APPROVED', completedAt: '2026-08-12T18:05:00' }),
      ],
    })

    renderPage('/evaluations/my?scope=COMPLETED')

    expect(await screen.findByText('진단 완료')).toBeTruthy()
    expect(screen.queryByText('신청 날짜')).toBeNull()
  })

  it('완료한 진단이 없으면 진행 중 목록으로 돌아갈 길을 준다', async () => {
    renderPage('/evaluations/my?scope=COMPLETED')

    expect(await screen.findByText('완료한 진단이 없습니다')).toBeTruthy()
  })
})
