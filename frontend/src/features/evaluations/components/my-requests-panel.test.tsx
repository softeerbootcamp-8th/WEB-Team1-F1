import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchMyRequests } from '../api'
import type { EvaluationSummary } from '../types'
import { MyRequestsPanel } from './my-requests-panel'

vi.mock('../api', () => ({
  fetchMyRequests: vi.fn(),
}))

const fetchMock = vi.mocked(fetchMyRequests)

function request(overrides: Partial<EvaluationSummary> = {}): EvaluationSummary {
  return {
    evaluationId: 1,
    status: 'REQUESTED',
    assigned: false,
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

function renderPanel(path = '/mypage/evaluations') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <MyRequestsPanel />
      </MemoryRouter>
    </QueryClientProvider> as ReactNode,
  )
}

describe('판매자 신청 내역의 진행 중 · 종료 구분', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({
      evaluations: [
        request({ evaluationId: 3, model: '아반떼 CN7', status: 'REJECTED' }),
        request({ evaluationId: 2, model: '쏘나타 DN8', status: 'REQUESTED', assigned: true }),
        request({ evaluationId: 1, model: '그랜저 IG', status: 'APPROVED' }),
      ],
    })
  })

  it('기본 목록에서 끝난 신청이 빠진다', async () => {
    renderPanel()

    expect(await screen.findByText(/쏘나타 DN8/)).toBeTruthy()
    // 반려된 건은 판매자가 더 할 일이 없다. 남아 있으면 진행 중인 신청이 그 아래 묻힌다
    expect(screen.queryByText(/아반떼 CN7/)).toBeNull()
  })

  it('출품할 수 있는 건이 맨 위에 이유와 함께 선다', async () => {
    // 진단이 끝나고 경매가 걸리지 않은 건은 판매자가 지금 손대야 하는 유일한 상태다
    renderPanel()

    const cards = await screen.findAllByRole('heading', { level: 2 })
    expect(cards[0].textContent).toContain('그랜저 IG')
    // 순서만 바뀌고 이유가 없으면 목록이 뒤섞인 것으로 읽힌다
    expect(screen.getByText('출품 대기')).toBeTruthy()
  })

  it('종료 탭에서 반려와 낙찰 완료를 본다', async () => {
    renderPanel()
    await screen.findByText(/쏘나타 DN8/)

    // Radix 탭은 mousedown에서 전환한다. click만 쏘면 아무 일도 일어나지 않는다
    fireEvent.mouseDown(screen.getByRole('tab', { name: /종료/ }))

    expect(await screen.findByText(/아반떼 CN7/)).toBeTruthy()
    expect(screen.queryByText(/쏘나타 DN8/)).toBeNull()
  })

  it('주소에 적힌 종료 목록으로 바로 들어오고, 상세 링크가 그 탭을 실어 보낸다', async () => {
    // 종료된 신청을 열어 보고 돌아왔을 때 진행 중으로 되돌아가 있으면 안 된다
    renderPanel('/mypage/evaluations?scope=CLOSED')

    expect(await screen.findByText(/아반떼 CN7/)).toBeTruthy()
    expect(screen.getByRole('link', { name: /신청 상세 보기/ }).getAttribute('href'))
      .toBe('/mypage/evaluations/3?scope=CLOSED')
  })

  it('신청을 한 번도 내지 않았으면 탭 대신 신청하기를 보여준다', async () => {
    // 양쪽이 다 비어 있는 탭 줄은 무엇을 고르라는 것인지 알려 주지 못한다
    fetchMock.mockResolvedValue({ evaluations: [] })

    renderPanel()

    expect(await screen.findByText('방문견적 신청 내역이 없습니다')).toBeTruthy()
    expect(screen.queryByRole('tab')).toBeNull()
  })
})
