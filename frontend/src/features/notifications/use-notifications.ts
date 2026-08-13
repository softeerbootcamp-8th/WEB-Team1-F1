import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/auth-context'
import type { AppNotification } from '@/types/domain'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  subscribeNotifications,
} from './api'
import { showNotificationToast } from './notification-toast'

/**
 * 서버가 준 목록과 화면이 들고 있던 목록을 합친다.
 *
 * 조회 응답은 요청이 서버에 닿은 순간의 사진이라, 그 뒤에 실시간으로 받은 알림이 빠져 있다.
 * 통째로 바꾸면 그 알림이 화면에서 사라진다. 연결 수명이 10분이라 정상 동작 중에도 주기적으로
 * 목록을 다시 읽으므로, 드물게 열리는 창이 아니라 상시 열리는 창이다.
 *
 * 읽음은 한쪽이라도 읽음이면 읽음으로 둔다. 읽음을 되돌리는 API가 없어 단방향이라 이 규칙이
 * 정보를 잃지 않는다. 서버 값으로 덮으면 방금 눌러 둔 알림이 안 읽음으로 되살아나고, 화면 값만
 * 지키면 다른 탭에서 읽은 것이 영영 반영되지 않는다.
 */
function mergeById(current: AppNotification[], incoming: AppNotification[]): AppNotification[] {
  const merged = new Map<number, AppNotification>()

  for (const notification of [...current, ...incoming]) {
    const seen = merged.get(notification.id)

    // 읽음 말고는 나중에 온 서버 값이 이긴다, 문구와 링크의 진실은 서버 한 곳이다
    merged.set(
      notification.id,
      seen ? { ...notification, read: seen.read || notification.read } : notification,
    )
  }

  // 이어 읽기가 내림차순을 전제로 서 있다, 합치면서 순서가 흐트러지면 다음 페이지가 엉뚱한 자리에 붙는다
  return [...merged.values()].sort((a, b) => b.id - a.id)
}

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
  const { isAuthenticated, user } = useAuth()
  const userId = user?.id
  const navigate = useNavigate()
  const location = useLocation()

  const [items, setItems] = useState<AppNotification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [cursor, setCursor] = useState<number | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)

  // 첫 연결의 onopen과 재연결의 onopen을 구분한다. 첫 연결은 아래 최초 적재와 겹친다
  const connectedOnce = useRef(false)

  // 연결 직후 서버가 건수를 한 번 보낸다. 그건 읽음이 바뀌어서 온 것이 아니라 배지를 맞추려는 것이라
  // 목록까지 다시 읽으면 재연결마다 조회가 두 번 나간다 — onOpen이 이미 읽고 있다
  const initialCountPending = useRef(false)

  // 회원이 바뀌거나 로그아웃하면 올린다. 이미 나간 조회는 취소할 수 없어서, 늦게 도착한 응답이
  // 지난 회원의 알림을 새 목록에 섞지 않도록 시작 시점의 값과 도착 시점의 값을 비교한다
  const generation = useRef(0)

  // 최초 적재가 다녀오는 사이에 건수가 달라졌는지. 조회가 담아 오는 값은 요청이 서버에 닿은
  // 순간의 것이라, 그 뒤에 온 값을 덮으면 배지가 실제보다 낮아진다
  const countChangedSinceLoad = useRef(false)

  // 지금 연결을 덮고 있는 목록 조회. 연결 직후 건수가 왔을 때 목록을 또 읽을지 이것으로 판정한다.
  // 참·거짓이 아니라 조회 자체를 들고 있는 이유는, 그 건수가 조회보다 먼저 도착하기 때문이다 —
  // 그 시점에는 성공 여부를 알 수 없어서 끝나기를 기다렸다가 실패했을 때만 다시 읽는다
  const coveringListRead = useRef<Promise<void> | null>(null)

  const loadFirstPage = useCallback(async () => {
    const startedAt = generation.current
    const page = await fetchNotifications()

    // 그사이 회원이 바뀌었으면 이 응답은 지난 회원의 것이다
    if (startedAt !== generation.current) return

    setItems((prev) => mergeById(prev, page.content))
    // 더 읽어 둔 뒤였다면 이어 읽기 지점이 앞으로 돌아간다. 다시 읽은 몫은 병합이 걸러 내므로
    // 목록이 어긋나지는 않고, 지점을 따로 기억하는 값을 하나 더 두는 것보다 단순하다
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
    countChangedSinceLoad.current = true
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
  // pathname만 보면 승인 알림처럼 query로 대상을 가르는 화면을 같은 곳으로 오판한다.
  // hash는 서버 알림 링크에 쓰지 않으므로 경로와 query만 비교한다.
  const currentTargetRef = useRef(`${location.pathname}${location.search}`)

  useEffect(() => {
    openRef.current = open
  }, [open])

  useEffect(() => {
    currentTargetRef.current = `${location.pathname}${location.search}`
  }, [location.pathname, location.search])

  // 최초 적재. 건수를 따로 묻는 이유는 실시간 연결이 막힌 환경에서도 배지가 맞아야 하기 때문이다
  useEffect(() => {
    // 회원이 바뀔 때도 지난다. 먼저 비워야 아래 병합이 이전 회원의 알림을 새 회원 목록에 섞지 않는다
    generation.current += 1
    countChangedSinceLoad.current = false
    coveringListRead.current = null
    setItems([])
    setUnreadCount(0)
    setCursor(null)
    setHasNext(false)

    if (!isAuthenticated || userId == null) return

    let cancelled = false
    setIsLoading(true)

    const firstLoad = Promise.all([fetchNotifications(), fetchUnreadCount()])
      .then(([page, count]) => {
        // 지난 회원의 응답이면 지금 회원의 표시를 건드리지 않는다
        if (cancelled) return

        // 이 조회가 다녀오는 사이에 실시간으로 도착한 알림이 있을 수 있다
        setItems((prev) => mergeById(prev, page.content))
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)

        // 실시간 연결이 살아 있으면 연결 직후 건수가 이미 도착해 있다. 그쪽이 더 나중 값이라
        // 여기서 덮지 않는다. 연결이 막힌 환경에서는 아무도 건수를 주지 않으므로 이 값이 쓰인다
        if (!countChangedSinceLoad.current) setUnreadCount(count)
      })

    // 첫 연결의 건수는 이 적재가 대신 목록을 맞춰 준다
    coveringListRead.current = firstLoad

    firstLoad
      // 알림은 헤더의 부가 요소라 실패해도 화면을 막지 않는다, 다음 적재가 진실을 준다
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [isAuthenticated, userId])

  // 로그인 여부만 보면 회원이 바뀌어도 이전 회원의 연결이 그대로 열려 있다. 지금은 로그아웃 없이
  // 회원이 바뀌는 경로가 없어 재현되지 않지만, 계정 전환이 생기는 순간 조용히 깨질 자리다
  useEffect(() => {
    if (!isAuthenticated || userId == null) return

    connectedOnce.current = false
    initialCountPending.current = false

    return subscribeNotifications({
      onNotification: ({ notification, unreadCount: count }) => {
        setItems((prev) =>
          prev.some((it) => it.id === notification.id) ? prev : [notification, ...prev],
        )
        countChangedSinceLoad.current = true
        setUnreadCount(count)

        // 지금 화면이 이미 같은 사건을 보여 주면 팝업만 생략한다. 위에서 목록과 배지는 먼저
        // 반영했으므로 기록은 남는다. 경매·거래 id나 query가 다르면 다른 대상이라 그대로 띄운다.
        // 배정 대기 목록은 실시간으로 다시 읽지 않으므로 그 화면에 있어도 새 신청 안내는 보여 준다.
        if (
          notification.type === 'EVAL_REQUESTED' ||
          notification.link !== currentTargetRef.current
        ) {
          showNotificationToast(notification, () => openRef.current(notification))
        }
      },
      onUnreadCount: (count) => {
        countChangedSinceLoad.current = true
        setUnreadCount(count)

        if (initialCountPending.current) {
          initialCountPending.current = false

          const covering = coveringListRead.current
          if (covering) {
            // 이 연결을 덮는 조회가 이미 돌고 있다. 성공하면 목록이 그것으로 맞춰지므로 여기서
            // 또 읽지 않고, 실패했을 때만 대신 읽는다. 성공 여부를 지금 알 수 없어 기다린다
            covering.catch(() => loadFirstPage().catch(() => undefined))
            return
          }
        }

        // 다른 화면에서 읽음이 바뀌었다는 뜻이다. 배지만 맞추면 목록에는 안 읽음 표시가 그대로 남는다.
        // 어느 알림이 읽혔는지를 실어 보내지 않는 것은 모두 읽음처럼 여러 건이 한꺼번에 바뀌는
        // 경우까지 담으면 전달 내용이 커지기 때문이다. 한 페이지는 10건이라 다시 읽는 편이 싸다
        loadFirstPage().catch(() => undefined)
      },
      onOpen: () => {
        // 곧 도착할 건수는 이 연결이 열려서 오는 것이다
        initialCountPending.current = true

        if (!connectedOnce.current) {
          connectedOnce.current = true
          return
        }

        // 재연결이다. 끊긴 사이에 쌓인 알림은 되짚어 오지 않으므로 목록을 다시 읽는다.
        // 배지는 서버가 연결 직후 건수를 한 번 보내 주므로 여기서 세지 않는다.
        // 이 조회가 곧 도착할 건수를 덮는다, 실패하면 그쪽이 대신 읽는다
        const reread = loadFirstPage()
        coveringListRead.current = reread
        reread.catch(() => undefined)
      },
      onClosed: () => {
        // 연결 종료는 안 읽은 건수의 변경을 뜻하지 않으므로 마지막 서버 값을 유지한다.
        // 세션이 실제로 만료되면 다음 요청의 401이 인증 상태를 바꾸고 위 비인증 분기가 초기화한다
      },
    })
  }, [isAuthenticated, userId, loadFirstPage])

  const loadMore = useCallback(async () => {
    if (cursor == null || isLoadingMore) return

    const startedAt = generation.current
    setIsLoadingMore(true)
    try {
      const page = await fetchNotifications(cursor)

      // 그사이 회원이 바뀌었으면 이 응답은 지난 회원의 것이다
      if (startedAt !== generation.current) return

      // 이어 읽기 지점이 앞으로 돌아간 뒤라면 이미 들고 있는 몫이 딸려 온다, 붙이지 않고 합친다
      setItems((prev) => mergeById(prev, page.content))
      setCursor(page.nextCursor)
      setHasNext(page.hasNext)
    } catch {
      // 추가 조회 실패는 화면을 막지 않는다. 다음 시도나 다음 적재가 진실을 준다
    } finally {
      setIsLoadingMore(false)
    }
  }, [cursor, isLoadingMore])

  const markAllRead = useCallback(() => {
    setItems((prev) => prev.map((it) => ({ ...it, read: true })))
    countChangedSinceLoad.current = true
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
