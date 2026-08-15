import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { assignEvaluation, fetchAssignableEvaluations } from '../api'
import type { AssignableEvaluation, AssignableEvaluationsResponse } from '../types'
import { AssignableEvaluationsPage } from './assignable-evaluations-page'

vi.mock('../api', () => ({
  fetchAssignableEvaluations: vi.fn(),
  assignEvaluation: vi.fn(),
}))

const fetchMock = vi.mocked(fetchAssignableEvaluations)
const assignMock = vi.mocked(assignEvaluation)

function evaluation(
  evaluationId: number,
  plateNumber: string,
  visitDate: string,
): AssignableEvaluation {
  return {
    evaluationId,
    plateNumber,
    manufacturer: 'HYUNDAI',
    model: '그랜저 IG',
    modelYear: 2021,
    fuelType: 'GASOLINE',
    transmission: 'AUTOMATIC',
    visitDate,
    visitAddress: '서울 성동구 왕십리로 83',
    requestedAt: '2026-08-10T09:00:00',
  }
}

function page(
  evaluations: AssignableEvaluation[],
  nextCursor: AssignableEvaluationsResponse['nextCursor'] = null,
): AssignableEvaluationsResponse {
  return { evaluations, hasNext: nextCursor !== null, nextCursor }
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AssignableEvaluationsPage />
      </MemoryRouter>
    </QueryClientProvider> as ReactNode,
  )
}

describe('배정 대기 목록', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    assignMock.mockReset()
  })

  it('더 보기를 누르면 직전 응답의 커서로 다음 페이지를 이어 읽는다', async () => {
    fetchMock.mockResolvedValueOnce(
      page([evaluation(520, '12가3456', '2026-08-20')], {
        visitDate: '2026-08-20',
        evaluationId: 520,
      }),
    )
    fetchMock.mockResolvedValueOnce(page([evaluation(521, '34나5678', '2026-08-25')]))

    renderPage()
    expect(await screen.findByText(/12가3456/)).toBeTruthy()

    // 첫 페이지는 커서 없이 받는다
    expect(fetchMock.mock.calls[0][0]).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: '더 보기' }))

    expect(await screen.findByText(/34나5678/)).toBeTruthy()
    // 서버가 준 커서를 그대로 돌려보낸다. 목록에서 몇 번째인지가 아니라 정렬축 위의 좌표다
    expect(fetchMock.mock.calls[1][0]).toEqual({
      visitDate: '2026-08-20',
      evaluationId: 520,
    })
    // 앞 페이지는 그대로 남는다
    expect(screen.getByText(/12가3456/)).toBeTruthy()
  })

  it('마지막까지 읽으면 더 보기 대신 끝났다고 알린다', async () => {
    fetchMock.mockResolvedValue(page([evaluation(520, '12가3456', '2026-08-20')]))

    renderPage()
    await screen.findByText(/12가3456/)

    // 목록이 짧아서 끝난 것인지 덜 받은 것인지 화면만 보고 구분할 수 있어야 한다
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
    expect(screen.getByText('배정 대기 중인 신청을 모두 확인했습니다.')).toBeTruthy()
  })

  /**
   * 수락한 뒤 목록 전체를 다시 받으면 이어 읽은 페이지를 처음부터 받게 되고, 그동안 보고 있던
   * 자리를 잃는다. 사라져야 하는 것은 방금 처리한 한 건뿐이다.
   */
  it('수락한 신청만 목록에서 내리고 나머지는 다시 받지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(
      page(
        [evaluation(520, '12가3456', '2026-08-20'), evaluation(521, '34나5678', '2026-08-20')],
        { visitDate: '2026-08-20', evaluationId: 521 },
      ),
    )
    fetchMock.mockResolvedValueOnce(page([evaluation(522, '56다7890', '2026-08-25')]))
    assignMock.mockResolvedValue({
      evaluationId: 520,
      plateNumber: '12가3456',
      visitDate: '2026-08-20',
      visitAddress: '서울 성동구 왕십리로 83',
      contactPhone: '01011112222',
      status: 'REQUESTED',
    })

    const { container } = renderPage()
    await screen.findByText(/12가3456/)
    fireEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await screen.findByText(/56다7890/)

    const fetchCallsBeforeAssign = fetchMock.mock.calls.length
    fireEvent.click(screen.getAllByRole('button', { name: '방문견적 수락' })[0])

    // 수락 결과 대화상자에도 번호판이 나오므로 목록 안에서만 확인한다.
    // 대화상자가 열리면 뒤쪽이 aria-hidden 이 되어 역할로는 목록을 찾을 수 없다
    const list = () => within(container.querySelector('ul') as HTMLElement)
    await waitFor(() => expect(list().queryByText(/12가3456/)).toBeNull())
    expect(list().getByText(/34나5678/)).toBeTruthy()
    expect(list().getByText(/56다7890/)).toBeTruthy()
    expect(fetchMock.mock.calls.length).toBe(fetchCallsBeforeAssign)
  })
})
