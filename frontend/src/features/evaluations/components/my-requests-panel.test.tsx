import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
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

describe('판매자 신청 내역의 큰 틀과 상태 구분', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({
      evaluations: [
        request({ evaluationId: 4, model: 'K5 DL3', status: 'APPROVED', auctionStatus: 'IN_PROGRESS' }),
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

  it('큰 틀은 탭이고 상태는 그 아래 칩이다', async () => {
    // 같은 줄에 같은 크기로 세우면 "진행 중"과 "출품 대기"가 형제로 읽혀
    // 무엇이 무엇을 품는지가 사라진다
    renderPanel()
    await screen.findByText(/쏘나타 DN8/)

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent?.replace(/\s/g, '')))
      .toEqual(['진행중3', '종료1'])

    const filter = screen.getByRole('group', { name: '진행 중 상태 필터' })
    expect(within(filter).getAllByRole('button').map((chip) => chip.textContent?.replace(/\s/g, '')))
      .toEqual(['배정대기0', '평가중1', '출품대기1', '경매예정0', '경매중1'])
  })

  it('출품할 수 있는 건이 맨 위에 이유와 함께 선다', async () => {
    // 진단이 끝나고 경매가 걸리지 않은 건은 판매자가 지금 손대야 하는 유일한 상태다
    renderPanel()

    const headings = await screen.findAllByRole('heading', { level: 2 })
    expect(headings[0].textContent).toContain('그랜저 IG')

    // 순서만 바뀌고 이유가 없으면 목록이 뒤섞인 것으로 읽힌다.
    // 칩에도 같은 말이 있고, 그 둘이 같은 말인 것이 맞다
    const topCard = headings[0].closest('[data-slot="card"]') as HTMLElement
    expect(within(topCard).getByText('출품 대기')).toBeTruthy()
  })

  it('칩으로 상태를 좁히고 다시 눌러 전체로 돌아온다', async () => {
    renderPanel()
    await screen.findByText(/쏘나타 DN8/)

    const chip = () => screen.getByRole('button', { name: /출품 대기/ })
    fireEvent.click(chip())

    expect(await screen.findByText(/그랜저 IG/)).toBeTruthy()
    expect(screen.queryByText(/쏘나타 DN8/)).toBeNull()
    expect(chip().getAttribute('aria-pressed')).toBe('true')

    // 켜진 것을 다시 누르면 전체다. "전체" 칩을 따로 두지 않는 이유다
    fireEvent.click(chip())

    expect(await screen.findByText(/쏘나타 DN8/)).toBeTruthy()
  })

  it('종료 탭은 반려와 낙찰을 담고 상태 칩도 그쪽 것으로 바뀐다', async () => {
    renderPanel()
    await screen.findByText(/쏘나타 DN8/)

    // Radix 탭은 mousedown에서 전환한다. click만 쏘면 아무 일도 일어나지 않는다
    fireEvent.mouseDown(screen.getByRole('tab', { name: /종료/ }))

    expect(await screen.findByText(/아반떼 CN7/)).toBeTruthy()
    const filter = screen.getByRole('group', { name: '종료 상태 필터' })
    expect(within(filter).getAllByRole('button').map((chip) => chip.textContent?.replace(/\s/g, '')))
      .toEqual(['반려1', '낙찰완료0'])
  })

  it('주소에 적힌 자리로 바로 들어오고, 상세 링크가 그 자리를 실어 보낸다', async () => {
    // 좁혀 보던 사람이 상세를 열어 보고 돌아왔을 때 기본 목록으로 튕기면 안 된다
    renderPanel('/mypage/evaluations?scope=CLOSED&state=REJECTED')

    expect(await screen.findByText(/아반떼 CN7/)).toBeTruthy()
    expect(screen.getByRole('link', { name: /신청 상세 보기/ }).getAttribute('href'))
      .toBe('/mypage/evaluations/3?scope=CLOSED&state=REJECTED')
  })

  it('큰 틀과 어긋난 상태는 버리고 그 틀 전체를 보여준다', async () => {
    // 두 값을 따로 실어 짝이 맞지 않는 주소가 만들어질 수 있다.
    // 그대로 두면 어느 칩도 켜지지 않은 채 빈 목록만 나온다
    renderPanel('/mypage/evaluations?scope=ACTIVE&state=REJECTED')

    expect(await screen.findByText(/쏘나타 DN8/)).toBeTruthy()
    expect(screen.getByRole('button', { name: /출품 대기/ }).getAttribute('aria-pressed'))
      .toBe('false')
  })

  it('좁혀 본 칸이 비면 필터를 끄는 길을 준다', async () => {
    // 배정 대기는 신청 직후 잠깐 머무는 상태라 대개 비어 있다
    renderPanel('/mypage/evaluations?state=PENDING_ASSIGNMENT')

    expect(await screen.findByText('배정을 기다리는 신청이 없습니다')).toBeTruthy()
    expect(screen.getByRole('button', { name: '진행 중 전체 보기' })).toBeTruthy()
  })

  it('신청을 한 번도 내지 않았으면 탭도 칩도 보여주지 않는다', async () => {
    // 전부 0인 조작 줄은 무엇을 고르라는 것인지 알려 주지 못한다
    fetchMock.mockResolvedValue({ evaluations: [] })

    renderPanel()

    expect(await screen.findByText('방문견적 신청 내역이 없습니다')).toBeTruthy()
    expect(screen.queryByRole('tab')).toBeNull()
    expect(screen.queryByRole('group')).toBeNull()
  })
})
