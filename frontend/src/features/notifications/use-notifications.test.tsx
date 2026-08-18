import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AppNotification } from '@/types/domain'
import { subscribeDealChanged } from '@/features/deals/deal-events'
import { dealDetailQueryKey } from '@/features/deals/query-keys'
import {
  ASSIGNABLE_EVALUATIONS_QUERY_KEY,
  MY_REQUESTS_QUERY_KEY,
  evaluationDetailQueryKey,
} from '@/features/evaluations/query-keys'

import { useNotifications } from './use-notifications'

const mocks = vi.hoisted(() => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  subscribeNotifications: vi.fn(),
  showNotificationToast: vi.fn(),
}))

type StreamHandlers = Parameters<
  typeof import('./api').subscribeNotifications
>[0]

let streamHandlers: StreamHandlers | null = null

vi.mock('@/features/auth/auth-context', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 7, role: 'EVALUATOR' },
  }),
}))

vi.mock('./api', () => ({
  fetchNotifications: mocks.fetchNotifications,
  fetchUnreadCount: mocks.fetchUnreadCount,
  markNotificationRead: mocks.markNotificationRead,
  markAllNotificationsRead: mocks.markAllNotificationsRead,
  subscribeNotifications: mocks.subscribeNotifications,
}))

vi.mock('./notification-toast', () => ({
  showNotificationToast: mocks.showNotificationToast,
}))

const EMPTY_PAGE = {
  content: [],
  serverTime: '2026-08-13T10:00:00',
  hasNext: false,
  nextCursor: null,
}

const REQUESTED_NOTIFICATION: AppNotification = {
  id: 41,
  type: 'EVAL_REQUESTED',
  message: '현대 그랜저 IG 12가3456 차량의 방문견적 신청이 접수되었습니다.',
  read: false,
  link: '/evaluations/assignable',
  createdAt: '2026-08-13T10:00:00',
}

const AUCTION_NOTIFICATION: AppNotification = {
  id: 42,
  type: 'AUCTION_STARTED',
  message: '그랜저 IG 경매가 시작되었습니다.',
  read: false,
  link: '/auctions/3',
  createdAt: '2026-08-13T10:01:00',
}

const DEAL_NOTIFICATION: AppNotification = {
  id: 43,
  type: 'DEAL_BUYER_SCHEDULE_REQUIRED',
  message: '판매자가 탁송 일정을 등록했습니다. 인도 일정을 정해 주세요.',
  read: false,
  link: '/mypage/deals/12',
  createdAt: '2026-08-13T10:02:00',
}

const WON_NOTIFICATION: AppNotification = {
  id: 44,
  type: 'AUCTION_WON',
  message: '낙찰되었습니다. 거래를 진행해 주세요.',
  read: false,
  link: '/mypage/deals/12',
  createdAt: '2026-08-13T10:03:00',
}

function testQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
}

function wrapper(initialEntry: string, queryClient: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

function renderNotifications(initialEntry = '/evaluations/my') {
  const queryClient = testQueryClient()

  const hook = renderHook(
    () => ({ notifications: useNotifications(), location: useLocation() }),
    { wrapper: wrapper(initialEntry, queryClient) },
  )

  return { ...hook, queryClient }
}

describe('useNotifications 의 새 방문견적 신청 연동', () => {
  beforeEach(() => {
    streamHandlers = null
    mocks.fetchNotifications.mockReset().mockResolvedValue(EMPTY_PAGE)
    mocks.fetchUnreadCount.mockReset().mockResolvedValue(0)
    mocks.markNotificationRead.mockReset().mockResolvedValue(undefined)
    mocks.markAllNotificationsRead.mockReset().mockResolvedValue(undefined)
    mocks.showNotificationToast.mockReset()
    mocks.subscribeNotifications.mockReset().mockImplementation((handlers: StreamHandlers) => {
      streamHandlers = handlers
      return () => undefined
    })
  })

  it('실시간 도착을 목록과 배지에 반영하고 안내를 누르면 읽음 처리 후 서버 링크로 이동한다', async () => {
    const { result } = renderNotifications()

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: REQUESTED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(result.current.notifications.items).toEqual([REQUESTED_NOTIFICATION])
    expect(result.current.notifications.unreadCount).toBe(1)
    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      REQUESTED_NOTIFICATION,
      expect.any(Function),
    )

    const openToast = mocks.showNotificationToast.mock.calls[0][1] as () => void
    act(() => openToast())

    expect(mocks.markNotificationRead).toHaveBeenCalledWith(REQUESTED_NOTIFICATION.id)
    expect(result.current.notifications.items[0].read).toBe(true)
    expect(result.current.notifications.unreadCount).toBe(0)
    expect(result.current.location.pathname).toBe('/evaluations/assignable')
  })

  it('화면을 새로 열면 저장된 신청 알림을 목록과 안 읽은 건수로 복구한다', async () => {
    mocks.fetchNotifications.mockResolvedValue({
      ...EMPTY_PAGE,
      content: [REQUESTED_NOTIFICATION],
    })
    mocks.fetchUnreadCount.mockResolvedValue(1)

    const { result } = renderNotifications()

    await waitFor(() => {
      expect(result.current.notifications.items).toEqual([REQUESTED_NOTIFICATION])
      expect(result.current.notifications.unreadCount).toBe(1)
    })

    expect(mocks.showNotificationToast).not.toHaveBeenCalled()
  })

  it('배정 대기 목록에 머물러 있어도 아직 화면에 없는 새 신청 안내를 표시한다', async () => {
    renderNotifications('/evaluations/assignable')

    await waitFor(() => expect(streamHandlers).not.toBeNull())
    act(() => {
      streamHandlers?.onNotification({
        notification: REQUESTED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      REQUESTED_NOTIFICATION,
      expect.any(Function),
    )
  })

  it('새 신청 알림이 도착하면 열려 있는 배정 대기 목록을 다시 조회한다', async () => {
    const fetchAssignable = vi.fn().mockResolvedValue({ evaluations: [] })
    const queryClient = testQueryClient()

    renderHook(
      () => {
        const notifications = useNotifications()
        useQuery({
          queryKey: ASSIGNABLE_EVALUATIONS_QUERY_KEY,
          queryFn: fetchAssignable,
        })
        return notifications
      },
      { wrapper: wrapper('/evaluations/assignable', queryClient) },
    )

    await waitFor(() => {
      expect(streamHandlers).not.toBeNull()
      expect(fetchAssignable).toHaveBeenCalledTimes(1)
    })

    act(() => {
      streamHandlers?.onNotification({
        notification: REQUESTED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    await waitFor(() => expect(fetchAssignable).toHaveBeenCalledTimes(2))
  })

  it('다른 화면에서 받으면 배정 대기 캐시를 오래된 상태로 표시한다', async () => {
    const { queryClient } = renderNotifications('/evaluations/my')
    queryClient.setQueryData(ASSIGNABLE_EVALUATIONS_QUERY_KEY, { evaluations: [] })

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: REQUESTED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(queryClient.getQueryState(ASSIGNABLE_EVALUATIONS_QUERY_KEY)?.isInvalidated).toBe(true)
  })

  it('다른 종류의 알림은 배정 대기 목록을 다시 조회하지 않는다', async () => {
    const fetchAssignable = vi.fn().mockResolvedValue({ evaluations: [] })
    const queryClient = testQueryClient()

    renderHook(
      () => {
        const notifications = useNotifications()
        useQuery({
          queryKey: ASSIGNABLE_EVALUATIONS_QUERY_KEY,
          queryFn: fetchAssignable,
        })
        return notifications
      },
      { wrapper: wrapper('/evaluations/assignable', queryClient) },
    )

    await waitFor(() => {
      expect(streamHandlers).not.toBeNull()
      expect(fetchAssignable).toHaveBeenCalledTimes(1)
    })

    act(() => {
      streamHandlers?.onNotification({
        notification: AUCTION_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(fetchAssignable).toHaveBeenCalledTimes(1)
  })

  it('실시간 연결이 다시 붙으면 끊긴 사이 저장된 신청 알림을 다시 조회한다', async () => {
    mocks.fetchNotifications
      .mockResolvedValueOnce(EMPTY_PAGE)
      .mockResolvedValueOnce({ ...EMPTY_PAGE, content: [REQUESTED_NOTIFICATION] })

    const { result } = renderNotifications()

    await waitFor(() => expect(mocks.fetchNotifications).toHaveBeenCalledTimes(1))
    act(() => streamHandlers?.onOpen())
    act(() => streamHandlers?.onOpen())

    await waitFor(() => {
      expect(mocks.fetchNotifications).toHaveBeenCalledTimes(2)
      expect(result.current.notifications.items).toEqual([REQUESTED_NOTIFICATION])
    })
  })
})

describe('useNotifications 의 거래 단계 연동', () => {
  beforeEach(() => {
    streamHandlers = null
    mocks.fetchNotifications.mockReset().mockResolvedValue(EMPTY_PAGE)
    mocks.fetchUnreadCount.mockReset().mockResolvedValue(0)
    mocks.markNotificationRead.mockReset().mockResolvedValue(undefined)
    mocks.markAllNotificationsRead.mockReset().mockResolvedValue(undefined)
    mocks.showNotificationToast.mockReset()
    mocks.subscribeNotifications.mockReset().mockImplementation((handlers: StreamHandlers) => {
      streamHandlers = handlers
      return () => undefined
    })
  })

  /**
   * 상대가 단계를 넘긴 순간이 곧 내 차례가 된 순간이다. 보고 있던 상세가 지난 단계로 남아 있으면
   * 사용자는 새로고침해야 자기 차례를 안다.
   */
  it('거래 단계 알림이 도착하면 열려 있는 거래 상세를 다시 조회한다', async () => {
    const fetchDetail = vi.fn().mockResolvedValue({ dealId: 12 })
    const queryClient = testQueryClient()

    renderHook(
      () => {
        const notifications = useNotifications()
        useQuery({ queryKey: dealDetailQueryKey(12), queryFn: fetchDetail })
        return notifications
      },
      { wrapper: wrapper('/mypage/deals/12', queryClient) },
    )

    await waitFor(() => {
      expect(streamHandlers).not.toBeNull()
      expect(fetchDetail).toHaveBeenCalledTimes(1)
    })

    act(() => {
      streamHandlers?.onNotification({
        notification: DEAL_NOTIFICATION,
        unreadCount: 1,
      })
    })

    await waitFor(() => expect(fetchDetail).toHaveBeenCalledTimes(2))
  })

  /** 다시 읽혀도 뱃지·버튼이 조용히 바뀔 뿐이라, 무엇이 달라졌는지는 문구가 말해 준다 */
  it('같은 거래 화면을 보고 있어도 안내를 띄운다', async () => {
    const { result } = renderNotifications('/mypage/deals/12')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: DEAL_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      DEAL_NOTIFICATION,
      expect.any(Function),
    )
    expect(result.current.notifications.items).toEqual([DEAL_NOTIFICATION])
    expect(result.current.notifications.unreadCount).toBe(1)
  })

  /** 목록은 조회 캐시 위에 있지 않아 무효화가 닿지 않는다, 신호를 따로 흘린다 */
  it('거래 단계 알림이 도착하면 거래 목록에도 다시 읽으라고 알린다', async () => {
    const reload = vi.fn()
    const unsubscribe = subscribeDealChanged(reload)

    renderNotifications('/mypage/purchases')
    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: DEAL_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(reload).toHaveBeenCalledTimes(1)
    unsubscribe()
  })

  /** 낙찰과 동시에 거래가 만들어져 구매 내역에 새 행이 생긴다 */
  it('낙찰 알림도 거래 목록을 다시 읽게 한다', async () => {
    const reload = vi.fn()
    const unsubscribe = subscribeDealChanged(reload)

    renderNotifications('/mypage/purchases')
    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: WON_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(reload).toHaveBeenCalledTimes(1)
    unsubscribe()
  })

  it('거래와 무관한 알림은 거래 목록을 건드리지 않는다', async () => {
    const reload = vi.fn()
    const unsubscribe = subscribeDealChanged(reload)

    renderNotifications('/mypage/purchases')
    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: AUCTION_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(reload).not.toHaveBeenCalled()
    unsubscribe()
  })
})

const APPROVED_NOTIFICATION: AppNotification = {
  id: 45,
  type: 'EVAL_APPROVED',
  message: '차량 평가가 승인되었습니다. 경매글을 등록해 주세요.',
  read: false,
  link: '/mypage/evaluations/9',
  createdAt: '2026-08-13T10:04:00',
}

const OUTBID_NOTIFICATION: AppNotification = {
  id: 46,
  type: 'OUTBID',
  message: '현대 그랜저 IG 경매에서 이*님이 24,850,000원에 상위 입찰했습니다.',
  read: false,
  link: '/auctions/3',
  createdAt: '2026-08-13T10:05:00',
}

describe('useNotifications 의 평가 결과 연동과 안내 표시 규칙', () => {
  beforeEach(() => {
    streamHandlers = null
    mocks.fetchNotifications.mockReset().mockResolvedValue(EMPTY_PAGE)
    mocks.fetchUnreadCount.mockReset().mockResolvedValue(0)
    mocks.markNotificationRead.mockReset().mockResolvedValue(undefined)
    mocks.markAllNotificationsRead.mockReset().mockResolvedValue(undefined)
    mocks.showNotificationToast.mockReset()
    mocks.subscribeNotifications.mockReset().mockImplementation((handlers: StreamHandlers) => {
      streamHandlers = handlers
      return () => undefined
    })
  })

  it('평가 결과 알림이 도착하면 열려 있는 신청 상세를 다시 조회한다', async () => {
    const fetchDetail = vi.fn().mockResolvedValue({ evaluationId: 9 })
    const queryClient = testQueryClient()

    renderHook(
      () => {
        const notifications = useNotifications()
        useQuery({ queryKey: evaluationDetailQueryKey(9), queryFn: fetchDetail })
        return notifications
      },
      { wrapper: wrapper('/mypage/evaluations/9', queryClient) },
    )

    await waitFor(() => {
      expect(streamHandlers).not.toBeNull()
      expect(fetchDetail).toHaveBeenCalledTimes(1)
    })

    act(() => {
      streamHandlers?.onNotification({
        notification: APPROVED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    await waitFor(() => expect(fetchDetail).toHaveBeenCalledTimes(2))
  })

  /** 목록의 상태 배지와 상세의 결과가 서로 다른 말을 하면 안 된다 */
  it('평가 결과 알림이 도착하면 내 신청 목록도 오래된 상태로 표시한다', async () => {
    const { queryClient } = renderNotifications('/mypage/evaluations/9')
    queryClient.setQueryData(MY_REQUESTS_QUERY_KEY, { evaluations: [] })

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: APPROVED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(queryClient.getQueryState(MY_REQUESTS_QUERY_KEY)?.isInvalidated).toBe(true)
  })

  it('같은 신청 상세를 보고 있어도 평가 결과 안내를 띄운다', async () => {
    renderNotifications('/mypage/evaluations/9')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: APPROVED_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      APPROVED_NOTIFICATION,
      expect.any(Function),
    )
  })

  /** 방 안에서는 현재가 숫자만 바뀌어, 안내가 없으면 밀려난 것을 알아채지 못한다 */
  it('같은 경매방을 보고 있어도 상위 입찰 안내를 띄운다', async () => {
    const { result } = renderNotifications('/auctions/3')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: OUTBID_NOTIFICATION,
        unreadCount: 1,
      })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      OUTBID_NOTIFICATION,
      expect.any(Function),
    )
    expect(result.current.notifications.items).toEqual([OUTBID_NOTIFICATION])
    expect(result.current.notifications.unreadCount).toBe(1)
  })
})

const SOLD_NOTIFICATION: AppNotification = {
  id: 47,
  type: 'AUCTION_SOLD',
  message: '등록하신 차량이 낙찰되었습니다.',
  read: false,
  link: '/auctions/3',
  createdAt: '2026-08-13T10:06:00',
}

const WON_IN_ROOM: AppNotification = {
  id: 48,
  type: 'AUCTION_WON',
  message: '낙찰되었습니다. 거래를 진행해 주세요.',
  read: false,
  link: '/mypage/deals/12',
  createdAt: '2026-08-13T10:06:00',
}

/**
 * 마감 순간에는 방이 스스로 세는 이동과 서버가 보내는 알림이 각자의 시계로 움직인다.
 * 어느 쪽이 먼저 도착하든 안내는 같아야 한다 — 아래 둘은 그 두 순서를 각각 세운 것이다.
 */
describe('useNotifications 의 마감 안내는 이동 순서에 좌우되지 않는다', () => {
  beforeEach(() => {
    streamHandlers = null
    mocks.fetchNotifications.mockReset().mockResolvedValue(EMPTY_PAGE)
    mocks.fetchUnreadCount.mockReset().mockResolvedValue(0)
    mocks.markNotificationRead.mockReset().mockResolvedValue(undefined)
    mocks.markAllNotificationsRead.mockReset().mockResolvedValue(undefined)
    mocks.showNotificationToast.mockReset()
    mocks.subscribeNotifications.mockReset().mockImplementation((handlers: StreamHandlers) => {
      streamHandlers = handlers
      return () => undefined
    })
  })

  it('알림이 결과 화면 이동보다 먼저 도착해 아직 방에 있어도 판매자 낙찰 안내를 띄운다', async () => {
    renderNotifications('/auctions/3')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({ notification: SOLD_NOTIFICATION, unreadCount: 1 })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      SOLD_NOTIFICATION,
      expect.any(Function),
    )
  })

  it('결과 화면으로 이동한 뒤 도착해도 판매자 낙찰 안내를 띄운다', async () => {
    renderNotifications('/auctions/3/result')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({ notification: SOLD_NOTIFICATION, unreadCount: 1 })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      SOLD_NOTIFICATION,
      expect.any(Function),
    )
  })

  /** 낙찰자 알림은 목적지가 거래라 경매방 주소와 겹칠 일이 없다 */
  it('경매방에 남아 있어도 낙찰 안내를 띄운다', async () => {
    renderNotifications('/auctions/3')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({ notification: WON_IN_ROOM, unreadCount: 1 })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(WON_IN_ROOM, expect.any(Function))
  })

  it('대기 중 미리보기에 있어도 경매 시작 안내를 띄운다', async () => {
    renderNotifications('/auctions?open=3')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({ notification: AUCTION_NOTIFICATION, unreadCount: 1 })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      AUCTION_NOTIFICATION,
      expect.any(Function),
    )
  })

  it('경매방 안에서 받은 시작 안내도 띄운다', async () => {
    renderNotifications('/auctions/3')

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({ notification: AUCTION_NOTIFICATION, unreadCount: 1 })
    })

    expect(mocks.showNotificationToast).toHaveBeenCalledWith(
      AUCTION_NOTIFICATION,
      expect.any(Function),
    )
  })
})

const FAILED_NOTIFICATION: AppNotification = {
  id: 49,
  type: 'AUCTION_FAILED',
  message: '등록하신 경매가 입찰 없이 종료되었습니다.',
  read: false,
  link: '/auctions/3',
  createdAt: '2026-08-13T10:07:00',
}

/**
 * 유찰이면 그 차량을 다시 등록할 수 있게 되는데, 그 판정이 신청 목록의 경매 상태 위에 서 있다.
 * 목록이 낡으면 "경매가 진행 중입니다"가 그대로 떠서 재등록 버튼이 나오지 않는다.
 */
describe('useNotifications 의 경매 마감과 신청 내역 연동', () => {
  beforeEach(() => {
    streamHandlers = null
    mocks.fetchNotifications.mockReset().mockResolvedValue(EMPTY_PAGE)
    mocks.fetchUnreadCount.mockReset().mockResolvedValue(0)
    mocks.markNotificationRead.mockReset().mockResolvedValue(undefined)
    mocks.markAllNotificationsRead.mockReset().mockResolvedValue(undefined)
    mocks.showNotificationToast.mockReset()
    mocks.subscribeNotifications.mockReset().mockImplementation((handlers: StreamHandlers) => {
      streamHandlers = handlers
      return () => undefined
    })
  })

  it('유찰 알림이 도착하면 열려 있는 신청 내역을 다시 조회한다', async () => {
    const fetchMyRequests = vi.fn().mockResolvedValue({ evaluations: [] })
    const queryClient = testQueryClient()

    renderHook(
      () => {
        const notifications = useNotifications()
        useQuery({ queryKey: MY_REQUESTS_QUERY_KEY, queryFn: fetchMyRequests })
        return notifications
      },
      { wrapper: wrapper('/mypage/evaluations', queryClient) },
    )

    await waitFor(() => {
      expect(streamHandlers).not.toBeNull()
      expect(fetchMyRequests).toHaveBeenCalledTimes(1)
    })

    act(() => {
      streamHandlers?.onNotification({ notification: FAILED_NOTIFICATION, unreadCount: 1 })
    })

    await waitFor(() => expect(fetchMyRequests).toHaveBeenCalledTimes(2))
  })

  it('낙찰 알림도 신청 내역을 오래된 상태로 표시한다', async () => {
    const { queryClient } = renderNotifications('/mypage/evaluations')
    queryClient.setQueryData(MY_REQUESTS_QUERY_KEY, { evaluations: [] })

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({ notification: SOLD_NOTIFICATION, unreadCount: 1 })
    })

    expect(queryClient.getQueryState(MY_REQUESTS_QUERY_KEY)?.isInvalidated).toBe(true)
  })

  it('입찰자에게 가는 종료 알림은 신청 내역을 건드리지 않는다', async () => {
    const { queryClient } = renderNotifications('/mypage/evaluations')
    queryClient.setQueryData(MY_REQUESTS_QUERY_KEY, { evaluations: [] })

    await waitFor(() => expect(streamHandlers).not.toBeNull())

    act(() => {
      streamHandlers?.onNotification({
        notification: {
          ...SOLD_NOTIFICATION,
          id: 50,
          type: 'AUCTION_ENDED',
          message: '경매가 종료되었습니다.',
        },
        unreadCount: 1,
      })
    })

    expect(queryClient.getQueryState(MY_REQUESTS_QUERY_KEY)?.isInvalidated).toBe(false)
  })
})
