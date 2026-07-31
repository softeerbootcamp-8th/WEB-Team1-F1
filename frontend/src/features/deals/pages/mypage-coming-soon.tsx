import { Link } from 'react-router-dom'
import { Hammer } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'

/** 마이페이지(거래·참여 경매)는 백엔드 Deal API가 아직 없어 임시 안내로 대체한다. */
export function MyPageComingSoon() {
  return (
    <main aria-label="마이페이지" className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        icon={Hammer}
        title="마이페이지는 개발중입니다"
        description="내 거래·참여 경매 조회는 준비 중이에요. 조금만 기다려 주세요."
        action={
          <Button asChild variant="outline">
            <Link to="/">홈으로</Link>
          </Button>
        }
      />
    </main>
  )
}
