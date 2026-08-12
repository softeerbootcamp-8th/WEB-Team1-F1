import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Bell, BellRing, LoaderCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAuth } from '@/features/auth/auth-context'
import { useStartAlert } from '@/features/auctions/use-start-alert'

interface StartAlertButtonProps {
  auctionId: number
  /** 안내 문구 정렬. 미리보기는 오른쪽 액션 자리, 대기방은 가운데다 */
  align?: 'center' | 'end'
  className?: string
}

/**
 * 경매 시작 알림 신청 버튼.
 *
 * 입장 전 미리보기와 대기방이 같은 컴포넌트를 쓴다 — 사용자가 경매를 어디서 발견했느냐로
 * 신청 가능 여부나 문구가 달라지면 안 되고, 상태 판정이 두 벌이 되는 순간 어긋난다.
 *
 * 시작 전 화면에서만 쓴다. 진행중·종료 화면은 이 버튼을 아예 그리지 않는다 —
 * 시작 알림의 목적이 사라진 뒤라 비활성 버튼조차 남길 이유가 없다.
 */
export function StartAlertButton({ auctionId, align = 'end', className }: StartAlertButtonProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated, isLoading: authLoading } = useAuth()
  const { phase, signedOut, subscribe } = useStartAlert(auctionId, isAuthenticated)

  // 세션이 끊긴 채 눌렀으면 로그인으로 옮기고, 마치면 보던 화면으로 돌려보낸다
  useEffect(() => {
    if (!signedOut) return
    navigate('/login', {
      state: { returnTo: { pathname: location.pathname, state: location.state } },
    })
  }, [signedOut, navigate, location.pathname, location.state])

  // 시작 전이 아니라고 서버가 답했다. 신청할 수 없는 경매라 자리를 비운다
  if (phase === 'UNAVAILABLE') return null

  const done = phase === 'DONE'
  const pending = phase === 'PENDING'
  const busy = pending || phase === 'LOADING' || authLoading

  return (
    <div
      className={cn(
        'flex flex-col gap-1.5',
        align === 'center' ? 'items-center' : 'items-end',
        className,
      )}
    >
      <Button
        // 신청이 끝나면 숨기지 않고 비활성으로 남긴다. 이미 신청했다는 사실 자체가
        // 사용자가 확인하려는 정보라, 사라지면 눌렀는지 기억에 의존하게 된다
        disabled={done || busy}
        variant={done ? 'outline' : 'default'}
        onClick={() => {
          // 비로그인은 눌러 봐야 401 이다. 누구에게 보낼 알림인지가 세션이라 로그인이 먼저다
          if (!isAuthenticated) {
            navigate('/login', {
              state: { returnTo: { pathname: location.pathname, state: location.state } },
            })
            return
          }
          subscribe()
        }}
      >
        {done ? <BellRing /> : pending ? <LoaderCircle className="animate-spin" /> : <Bell />}
        {done ? '시작 알림 신청 완료' : pending ? '신청하는 중' : '경매 시작 알림 받기'}
      </Button>

      {/* 진행 상태와 완료는 소리로도 전해져야 한다, 버튼 문구만 바뀌면 낭독기가 지나친다 */}
      <p
        role="status"
        aria-live="polite"
        className={cn(
          'text-muted-foreground text-xs',
          align === 'center' ? 'text-center' : 'text-right',
        )}
      >
        {done
          ? '경매가 시작되면 알림을 보내 드립니다'
          : pending
            ? '신청을 처리하고 있습니다'
            : '한 번만 발송되며 신청 후에는 취소할 수 없습니다'}
      </p>
    </div>
  )
}
