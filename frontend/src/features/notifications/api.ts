import { axiosInstance } from '@/lib/axios'
import type { AppNotification } from '@/types/domain'

/** GET /api/notifications 한 페이지. */
export interface NotificationPage {
  content: AppNotification[]
  serverTime: string
  hasNext: boolean
  nextCursor: number | null
}

/** 커서 없이 부르면 첫 페이지, 이후에는 직전 응답의 nextCursor를 그대로 돌려보낸다. */
export async function fetchNotifications(cursor?: number): Promise<NotificationPage> {
  const { data } = await axiosInstance.get<NotificationPage>('/api/notifications', {
    params: cursor == null ? undefined : { cursor },
  })
  return data
}

/** GET /api/notifications/unread-count. 실시간 연결이 막힌 환경에서도 배지를 맞추기 위한 통로다. */
export async function fetchUnreadCount(): Promise<number> {
  const { data } = await axiosInstance.get<{ unreadCount: number }>(
    '/api/notifications/unread-count',
  )
  return data.unreadCount
}

/** 이미 읽은 알림에 다시 보내도 성공한다(서버가 멱등하게 처리한다). */
export async function markNotificationRead(id: number): Promise<void> {
  await axiosInstance.patch(`/api/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await axiosInstance.patch('/api/notifications/read-all')
}

/** 실시간으로 도착한 새 알림. 목록 조회와 같은 모양의 알림 + 그 시점의 안 읽은 건수. */
interface NotificationPush {
  notification: AppNotification
  unreadCount: number
}

interface StreamHandlers {
  onNotification: (push: NotificationPush) => void
  onUnreadCount: (unreadCount: number) => void
  /**
   * 연결이 열릴 때마다(재연결 포함). 끊긴 사이의 알림은 서버가 되짚어 보내지 않으므로
   * 여기서 목록을 다시 읽는다. 스트림 수명이 10분이라 정상 상태에서도 주기적으로 불린다.
   */
  onOpen: () => void
  /** 브라우저가 재시도를 포기한 경우 — 세션이 만료되면 재연결이 401을 받고 연결이 닫힌다. */
  onClosed: () => void
}

/**
 * GET /api/notifications/stream 구독. 회원별 채널이라 경매방을 보고 있지 않아도 알림이 온다.
 *
 * EventSource는 연결이 끊기면 스스로 다시 붙는다. 다만 재연결 응답이 2xx가 아니면 표준대로
 * 재시도를 포기하고 CLOSED로 남으므로, 그 경우만 화면에 알린다.
 */
export function subscribeNotifications(handlers: StreamHandlers): () => void {
  const baseURL = axiosInstance.defaults.baseURL ?? ''
  const source = new EventSource(`${baseURL}/api/notifications/stream`, {
    withCredentials: true,
  })

  source.onopen = () => handlers.onOpen()

  source.addEventListener('notification', (event) => {
    handlers.onNotification(JSON.parse((event as MessageEvent).data) as NotificationPush)
  })

  source.addEventListener('unread-count', (event) => {
    const { unreadCount } = JSON.parse((event as MessageEvent).data) as { unreadCount: number }
    handlers.onUnreadCount(unreadCount)
  })

  source.onerror = () => {
    if (source.readyState === EventSource.CLOSED) handlers.onClosed()
  }

  return () => source.close()
}
