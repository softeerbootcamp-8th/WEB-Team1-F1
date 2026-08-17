import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { activateUser, fetchUserDetail, fetchUsers, suspendUser } from '../api'
import type { UserDetail, UserSummary } from '../types'
import { AdminUsersPage } from './admin-users-page'

vi.mock('../api', () => ({
  fetchUsers: vi.fn(),
  fetchUserDetail: vi.fn(),
  suspendUser: vi.fn(),
  activateUser: vi.fn(),
}))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

const fetchUsersMock = vi.mocked(fetchUsers)
const fetchDetailMock = vi.mocked(fetchUserDetail)
const suspendMock = vi.mocked(suspendUser)
const activateMock = vi.mocked(activateUser)

const NO_CONDITION = { keyword: '', role: null, status: null, page: 0 }

function summary(overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id: 42,
    username: 'race_kim',
    realName: '김레이스',
    role: 'DEALER',
    status: 'ACTIVE',
    joinedAt: '2026-07-01T10:00:00',
    ...overrides,
  }
}

function detail(overrides: Partial<UserDetail> = {}): UserDetail {
  return {
    ...summary(),
    email: 'race@race.kr',
    phone: '01012345678',
    suspendReason: null,
    ...overrides,
  }
}

function givenUsers(users: UserSummary[], page = 0, totalPages = 1, totalUsers = users.length) {
  fetchUsersMock.mockResolvedValue({ users, page, totalPages, totalUsers })
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/users']}>
        <AdminUsersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('관리자 회원 관리', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('처음에는 조건 없이 전체 회원의 첫 페이지를 읽는다', async () => {
    givenUsers([summary()], 0, 1, 1)

    renderPage()

    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())
    expect(fetchUsersMock).toHaveBeenCalledWith(NO_CONDITION)
    expect(screen.getByText('총 1명')).toBeTruthy()
  })

  /*
   * 이 검색은 서버에서 인덱스를 타지 못해 전체를 훑는다. 타이핑마다 보내면 한 글자에 한 번씩
   * 풀스캔이 나가므로, 제출한 순간에만 조회해야 한다
   */
  it('검색어는 제출할 때만 조회에 걸린다', async () => {
    givenUsers([])

    renderPage()
    await waitFor(() => expect(fetchUsersMock).toHaveBeenCalledTimes(1))

    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: 'race_kim' } })

    expect(fetchUsersMock).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '검색' }))

    await waitFor(() =>
      expect(fetchUsersMock).toHaveBeenCalledWith({ ...NO_CONDITION, keyword: 'race_kim' }),
    )
  })

  it('상태 탭을 옮기면 그 상태로 다시 읽는다', async () => {
    givenUsers([])

    renderPage()
    await waitFor(() => expect(fetchUsersMock).toHaveBeenCalledTimes(1))

    fireEvent.mouseDown(screen.getByRole('tab', { name: '이용 정지' }))

    await waitFor(() =>
      expect(fetchUsersMock).toHaveBeenCalledWith({ ...NO_CONDITION, status: 'SUSPENDED' }),
    )
  })

  /*
   * 3페이지를 보던 중 조건을 좁히면 그 조건에는 3페이지가 없다. 페이지를 물고 가면 빈 목록이
   * 나오고 관리자는 결과가 없다고 읽는다
   */
  it('조건을 바꾸면 첫 페이지로 돌아간다', async () => {
    givenUsers([summary()], 1, 3, 47)

    renderPage()
    // 이동 버튼은 응답이 도착한 뒤에야 그려진다. 호출만 기다리면 아직 없다
    await waitFor(() => expect(screen.getByRole('button', { name: /다음/ })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: /다음/ }))
    await waitFor(() => expect(fetchUsersMock).toHaveBeenCalledWith({ ...NO_CONDITION, page: 1 }))

    fireEvent.mouseDown(screen.getByRole('tab', { name: '이용 정지' }))

    await waitFor(() =>
      expect(fetchUsersMock).toHaveBeenCalledWith({ ...NO_CONDITION, status: 'SUSPENDED' }),
    )
  })

  it('페이지가 하나뿐이면 이동 버튼을 두지 않는다', async () => {
    givenUsers([summary()], 0, 1, 1)

    renderPage()

    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())
    expect(screen.queryByRole('navigation', { name: '페이지 이동' })).toBeNull()
  })

  it('회원을 누르면 상세를 읽어 연락처까지 보여준다', async () => {
    givenUsers([summary()])
    fetchDetailMock.mockResolvedValue(detail())

    renderPage()
    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())

    fireEvent.click(screen.getByText('김레이스'))

    await waitFor(() => expect(screen.getByText('race@race.kr')).toBeTruthy())
    expect(fetchDetailMock).toHaveBeenCalledWith(42)
    // 저장은 숫자만, 사람이 읽는 자리에서는 하이픈을 붙인다
    expect(screen.getByText('010-1234-5678')).toBeTruthy()
  })

  it('사유를 적어 이용을 정지한다', async () => {
    givenUsers([summary()])
    fetchDetailMock.mockResolvedValue(detail())
    suspendMock.mockResolvedValue({
      id: 42,
      role: 'DEALER',
      status: 'SUSPENDED',
      suspendReason: '허위 매물을 반복 등록했습니다.',
    })

    renderPage()
    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())
    fireEvent.click(screen.getByText('김레이스'))
    await waitFor(() => expect(screen.getByRole('button', { name: '이용 정지' })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: '이용 정지' }))
    fireEvent.change(screen.getByLabelText('정지 사유'), {
      target: { value: '허위 매물을 반복 등록했습니다.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '정지하기' }))

    await waitFor(() =>
      expect(suspendMock).toHaveBeenCalledWith(42, '허위 매물을 반복 등록했습니다.'),
    )
  })

  // 사유 없이 정지하면 나중에 왜 막았는지 아무도 알 수 없다. 서버도 400 을 주지만 여기서 먼저 막는다
  it('사유가 비면 정지 요청을 보내지 않는다', async () => {
    givenUsers([summary()])
    fetchDetailMock.mockResolvedValue(detail())

    renderPage()
    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())
    fireEvent.click(screen.getByText('김레이스'))
    await waitFor(() => expect(screen.getByRole('button', { name: '이용 정지' })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: '이용 정지' }))
    fireEvent.change(screen.getByLabelText('정지 사유'), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: '정지하기' }))

    await waitFor(() => expect(suspendMock).not.toHaveBeenCalled())
  })

  it('정지된 회원에게는 해제 버튼이 나오고 사유를 묻지 않는다', async () => {
    givenUsers([summary({ status: 'SUSPENDED' })])
    fetchDetailMock.mockResolvedValue(
      detail({ status: 'SUSPENDED', suspendReason: '허위 매물을 반복 등록했습니다.' }),
    )
    activateMock.mockResolvedValue({
      id: 42,
      role: 'DEALER',
      status: 'ACTIVE',
      suspendReason: null,
    })

    renderPage()
    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())
    fireEvent.click(screen.getByText('김레이스'))
    await waitFor(() => expect(screen.getByRole('button', { name: '정지 해제' })).toBeTruthy())

    expect(screen.getByText('허위 매물을 반복 등록했습니다.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '이용 정지' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: '정지 해제' }))

    await waitFor(() => expect(activateMock).toHaveBeenCalledWith(42))
  })

  // 서버가 관리자·평가사를 정지 대상에서 빼 둔다. 누를 수 있게 두면 400 을 받으려고 누르는 버튼이 된다
  it('관리자와 평가사에게는 정지 버튼을 두지 않는다', async () => {
    givenUsers([summary({ role: 'EVALUATOR' })])
    fetchDetailMock.mockResolvedValue(detail({ role: 'EVALUATOR' }))

    renderPage()
    await waitFor(() => expect(screen.getByText('김레이스')).toBeTruthy())
    fireEvent.click(screen.getByText('김레이스'))

    await waitFor(() => expect(screen.getByText('race@race.kr')).toBeTruthy())
    expect(screen.queryByRole('button', { name: '이용 정지' })).toBeNull()
    expect(screen.queryByRole('button', { name: '정지 해제' })).toBeNull()
  })

  it('조건에 맞는 회원이 없으면 비어 있음을 알린다', async () => {
    givenUsers([], 0, 0, 0)

    renderPage()

    await waitFor(() => expect(screen.getByText('조건에 맞는 회원이 없습니다')).toBeTruthy())
  })
})
