import { CheckCheck, ClipboardList } from 'lucide-react'
import { describe, expect, it } from 'vitest'

import { NOTIFICATION_ICON } from './notification-icon'

describe('NOTIFICATION_ICON', () => {
  it('새 방문견적 신청은 평가 업무 아이콘으로 표시한다', () => {
    expect(NOTIFICATION_ICON.EVAL_REQUESTED).toBe(ClipboardList)
  })

  it('기존 평가 승인 알림의 표시는 바꾸지 않는다', () => {
    expect(NOTIFICATION_ICON.EVAL_APPROVED).toBe(CheckCheck)
  })
})
