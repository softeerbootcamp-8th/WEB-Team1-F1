import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DemoVehicleHelp } from './demo-vehicle-help'
import { fetchDemoVehicles } from '@/features/vehicle/api'

vi.mock('@/features/vehicle/api', () => ({
  fetchDemoVehicles: vi.fn(),
}))

const fetchMock = vi.mocked(fetchDemoVehicles)

function renderHelp() {
  // 재시도를 끄지 않으면 실패 시나리오가 기다리기만 한다
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <DemoVehicleHelp />
    </QueryClientProvider> as ReactNode,
  )
}

describe('데모 차량 도움말', () => {
  beforeEach(() => {
    fetchMock.mockReset()
  })

  it('불러오는 중임을 알린다', () => {
    fetchMock.mockReturnValue(new Promise(() => {}))

    renderHelp()

    expect(screen.getByRole('status').textContent).toContain(
      '차량 목록을 불러오는 중입니다',
    )
  })

  it('불러오지 못한 것과 차량이 없는 것을 다른 말로 알린다', async () => {
    // 둘을 "표가 비어 있음"으로 뭉치면 기다려야 하는지 다시 열어야 하는지 알 수 없다
    fetchMock.mockRejectedValue(new Error('network'))

    renderHelp()

    // 로딩 안내도 role="status" 라 역할이 아니라 문구로 기다린다
    expect(await screen.findByText('차량 목록을 불러오지 못했습니다.')).toBeTruthy()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeTruthy()
  })

  it('사용할 수 있는 차량이 없으면 빈 목록임을 알린다', async () => {
    fetchMock.mockResolvedValue([])

    renderHelp()

    expect(
      await screen.findByText('지금은 사용할 수 있는 데모 차량이 없습니다.'),
    ).toBeTruthy()
  })

  it('받은 차량을 서버가 준 순서 그대로 표에 그린다', async () => {
    // 목록 순서는 서버가 정한다(id 오름차순). 화면이 다시 정렬하면
    // "앞의 차가 등록되면 다음 차가 올라온다"는 동작이 화면에서 깨진다
    fetchMock.mockResolvedValue([
      {
        plateNumber: '11나1111',
        ownerName: '나나나',
        manufacturer: 'KIA',
        model: 'K8',
        modelYear: 2023,
      },
      {
        plateNumber: '21가2101',
        ownerName: '박도움',
        manufacturer: 'HYUNDAI',
        model: '팰리세이드',
        modelYear: 2023,
      },
    ])

    renderHelp()

    const rows = await screen.findAllByRole('row')
    const plates = rows.slice(1).map((row) => row.textContent)

    expect(rows[0].textContent).toBe('차량 번호이름차량')
    expect(rows[1].textContent).toBe('11나1111나나나기아 K8 2023년')
    expect(plates[0]).toContain('11나1111')
    expect(plates[0]).toContain('기아 K8')
    expect(plates[1]).toContain('21가2101')
  })
})
