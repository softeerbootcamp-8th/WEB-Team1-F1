import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/auth-context'
import { consumeJustSignedUp } from '@/lib/signup-welcome'
import type { AppNotification } from '@/types/domain'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  subscribeNotifications,
} from './api'
import { showNotificationToast } from './notification-toast'

// 가입 성공 안내가 먼저 뜨고 사라진 뒤에 환영 알림을 보여 준다. 둘이 겹치면 어느 것도 읽히지 않는다
const WELCOME_TOAST_DELAY_MILLIS = 3_000

/**
 * 헤더 알림의 상태 한 곳. 목록·안 읽은 건수·실시간 구독을 함께 들고 있다.
 *
 * 조회 캐시(react-query)를 쓰지 않는 것은 이 프로젝트가 feature 훅에 상태를 두는 방식으로
 * 통일돼 있기 때문이다(use-auction-list). 알림 하나 때문에 상태 관리 방식을 새로 들이지 않는다.
 *
 * 연결을 여는 자리도 여기 하나다. 벨은 헤더에 붙어 있고 헤더는 레이아웃과 함께 계속 떠 있으므로,
 * 화면을 옮겨도 연결이 끊기지 않는다. 로그인 상태에서만 열어 브라우저의 도메인당 연결 수를 아낀다.
 */
export function useNotifications() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const [items, setItems] = useState<AppNotification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [cursor, setCursor] = useState<number | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)

  // 첫 연결의 onopen과 재연결의 onopen을 구분한다. 첫 연결은 아래 최초 적재와 겹친다
  const connectedOnce = useRef(false)

  const loadFirstPage = useCallback(async () => {
    const page = await fetchNotifications()
    setItems(page.content)
    setCursor(page.nextCursor)
    setHasNext(page.hasNext)
  }, [])

  /**
   * 읽음으로 바꾼다. 이동은 하지 않는다.
   * 목록은 행 자체가 링크라서 이동을 라우터가 맡고, 여기서 또 옮기면 두 번 이동한다.
   */
  const markRead = useCallback((notification: AppNotification) => {
    if (notification.read) return

    setItems((prev) => prev.map((it) => (it.id === notification.id ? { ...it, read: true } : it)))
    setUnreadCount((prev) => Math.max(0, prev - 1))
    // 서버가 멱등해서 실패해도 다음 조회가 진실을 준다
    markNotificationRead(notification.id).catch(() => undefined)
  }, [])

  /** 읽음으로 바꾸고 그 알림이 가리키는 화면으로 옮긴다. 링크가 아닌 곳(안내 토스트)에서 쓴다. */
  const open = useCallback(
    (notification: AppNotification) => {
      markRead(notification)
      navigate(notification.link)
    },
    [markRead, navigate],
  )

  /**
   * 효과들이 open을 직접 의존하지 않도록 최신 함수를 ref에 담아 둔다.
   * open은 라우터의 navigate에 묶여 있고 navigate는 화면을 옮길 때마다 새 함수가 되는데,
   * 그것을 의존성에 넣으면 화면을 옮길 때마다 연결이 끊기고 다시 붙는다. 실제로 그렇게 동작했다.
   */
  const openRef = useRef(open)

  useEffect(() => {
    openRef.current = open
  }, [open])

  // 최초 적재. 건수를 따로 묻는 이유는 실시간 연결이 막힌 환경에서도 배지가 맞아야 하기 때문이다
  useEffect(() => {
    if (!isAuthenticated) {
      setItems([])
      setUnreadCount(0)
      setCursor(null)
      setHasNext(false)
      return
    }

    let cancelled = false
    let welcomeTimer: number | undefined
    setIsLoading(true)

    Promise.all([fetchNotifications(), fetchUnreadCount()])
      .then(([page, count]) => {
        if (cancelled) return
        setItems(page.content)
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)
        setUnreadCount(count)

        // 가입 직후 딱 한 번, 화면이 환영 알림을 대신 안내한다.
        // 이 알림은 발행 시점에 구독이 없어 실시간으로 올 수 없어서 배지에만 잡히기 때문이다.
        // 문구는 서버가 보관한 것을 그대로 쓰고, 눌렀을 때 동작도 목록에서 누른 것과 같다.
        // 표시는 한 번 읽으면 사라지므로 다음 로그인에는 뜨지 않는다
        const welcome = page.content[0]

        if (welcome && !welcome.read && consumeJustSignedUp()) {
          welcomeTimer = window.setTimeout(() => {
            showNotificationToast(welcome, () => openRef.current(welcome))
          }, WELCOME_TOAST_DELAY_MILLIS)
        }
      })
      // 알림은 헤더의 부가 요소라 실패해도 화면을 막지 않는다, 다음 적재가 진실을 준다
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    return () => {
      cancelled = true
      window.clearTimeout(welcomeTimer)
    }
  }, [isAuthenticated])

  useEffect(() => {
    if (!isAuthenticated) return

    connectedOnce.current = false

    return subscribeNotifications({
      onNotification: ({ notification, unreadCount: count }) => {
        setItems((prev) =>
          prev.some((it) => it.id === notification.id) ? prev : [notification, ...prev],
        )
        setUnreadCount(count)

        // 위치는 전역 토스트(top-center)를 그대로 쓴다. 헤더 메뉴를 잠깐 가리지만,
        // 눈에 먼저 들어오는 자리가 화면 위쪽이고 알림은 놓치면 값이 없다
        showNotificationToast(notification, () => openRef.current(notification))
      },
      onUnreadCount: setUnreadCount,
      onOpen: () => {
        if (!connectedOnce.current) {
          connectedOnce.current = true
          return
        }

        // 재연결이다. 끊긴 사이에 쌓인 알림은 되짚어 오지 않으므로 목록을 다시 읽는다.
        // 배지는 서버가 연결 직후 건수를 한 번 보내 주므로 여기서 세지 않는다
        loadFirstPage().catch(() => undefined)
      },
      onClosed: () => {
        // 세션이 만료돼 브라우저가 재시도를 포기한 상태다. 조용히 멈춘다 —
        // 다음 요청이 401을 받으면 인증 흐름이 로그인으로 보낸다
        setUnreadCount(0)
      },
    })
  }, [isAuthenticated, loadFirstPage])

  const loadMore = useCallback(async () => {
    if (cursor == null || isLoadingMore) return

    setIsLoadingMore(true)
    try {
      const page = await fetchNotifications(cursor)
      setItems((prev) => [...prev, ...page.content])
      setCursor(page.nextCursor)
      setHasNext(page.hasNext)
    } finally {
      setIsLoadingMore(false)
    }
  }, [cursor, isLoadingMore])

  const markAllRead = useCallback(() => {
    setItems((prev) => prev.map((it) => ({ ...it, read: true })))
    setUnreadCount(0)
    markAllNotificationsRead().catch(() => undefined)
  }, [])

  return {
    items,
    unreadCount,
    hasNext,
    isLoading,
    isLoadingMore,
    loadMore,
    markAllRead,
    markRead,
  }
}
