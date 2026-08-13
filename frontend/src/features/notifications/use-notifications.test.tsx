import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AppNotification } from '@/types/domain'

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
  message: '12가3456 그랜저 IG 차량의 방문 진단 신청이 접수되었습니다.',
  read: false,
  link: '/evaluations/assignable',
  createdAt: '2026-08-13T10:00:00',
}

function renderNotifications(initialEntry = '/evaluations/my') {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  )

  return renderHook(
    () => ({ notifications: useNotifications(), location: useLocation() }),
    { wrapper },
  )
}

describe('useNotifications 의 새 방문 진단 신청 연동', () => {
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
