import { ChevronRight, X } from 'lucide-react'
import { toast } from 'sonner'

import type { AppNotification } from '@/types/domain'
import { NOTIFICATION_ICON } from './notification-icon'

/**
 * 누를 시간을 준다. 기본 4초는 문구를 읽고 손을 옮기기에 짧다 —
 * 이동까지 할 수 있는 안내라면 읽는 시간과 누르는 시간이 둘 다 필요하다
 */
const DURATION_MILLIS = 6_000

/**
 * 새 알림 안내.
 *
 * <b>카드 전체가 이동이고 우상단 X 만 닫기다.</b> 작은 버튼 하나를 눌러야 하면 급할 때 빗나가고,
 * 반대로 전체가 이동인데 닫기가 없으면 닫으려다 이동해 버린다. 두 동작을 자리로 갈라 둔다.
 *
 * <b>이 안내는 표시 전용이다.</b> 뜨는 것만으로는 읽음이 되지 않는다. 자리를 비운 사이 지나간 안내를
 * 읽음으로 처리하면, 알림함은 비었는데 아무것도 못 본 상태가 된다.
 *
 * sonner 의 action 버튼을 쓰지 않고 직접 그리는 이유가 그것이다. action 은 버튼 하나만 만들 수 있어서
 * "카드 전체 클릭"을 표현할 수 없다. 색과 여백은 전역 토큰을 그대로 써서 다크 모드가 함께 따라온다.
 */
export function showNotificationToast(notification: AppNotification, onOpen: () => void): void {
  const Icon = NOTIFICATION_ICON[notification.type]

  toast.custom(
    (id) => (
      <div className="bg-popover text-popover-foreground border-border relative flex w-[27rem] items-start gap-4 rounded-xl border p-5 shadow-lg">
        {/* 카드 전체가 이동 영역이다. X 를 덮지 않도록 오른쪽에 여백을 둔다 */}
        <button
          type="button"
          onClick={() => {
            toast.dismiss(id)
            onOpen()
          }}
          className="group flex flex-1 items-start gap-4 pr-6 text-left"
        >
          <span className="bg-muted flex size-11 shrink-0 items-center justify-center rounded-full">
            <Icon className="size-5" aria-hidden />
          </span>

          <span className="min-w-0 flex-1 space-y-1.5">
            <span className="block text-base leading-snug font-medium">
              {notification.message}
            </span>
            <span className="text-muted-foreground flex items-center gap-0.5 text-sm">
              확인하기
              <ChevronRight
                className="size-4 transition-transform group-hover:translate-x-0.5"
                aria-hidden
              />
            </span>
          </span>
        </button>

        <button
          type="button"
          onClick={() => toast.dismiss(id)}
          aria-label="알림 닫기"
          className="text-muted-foreground/60 hover:text-foreground hover:bg-muted absolute top-2.5 right-2.5 flex size-7 items-center justify-center rounded-md transition-colors"
        >
          <X className="size-4" aria-hidden />
        </button>
      </div>
    ),
    { duration: DURATION_MILLIS },
  )
}
