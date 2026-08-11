import { ArrowLeft } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

import { Button } from '@/components/ui/button'

/**
 * 왔던 목록으로 돌아가는 버튼. 목록 카드가 state.from에 자기 주소(탭 포함)를 실어 보내고,
 * 여기는 그 주소로 가는 평범한 Link다. 목록 캐시가 데이터와 스크롤을 되살리므로 앞으로
 * 가는 이동이어도 화면은 뒤로가기와 똑같이 보던 자리로 돌아간다.
 *
 * navigate(-1)을 쓰지 않는 이유: 공유 링크로 방에 바로 들어와 로그인을 마친 경우,
 * 로그인 왕복이 replace로 이뤄져 location.key가 default가 아닌데도 히스토리 아래에
 * 앱 화면이 없다. 이때 -1은 앱 밖으로 나간다. from이 없으면 목록 첫 화면으로 보낸다.
 */
export function BackLink() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  // 다른 곳으로 새는 값 방어. 로그인 페이지의 returnTo 검증과 같은 기준이다.
  const target = from?.startsWith('/') && !from.startsWith('//') ? from : '/auctions'

  return (
    <Button variant="ghost" size="sm" className="mb-4 -ml-2" asChild>
      <Link to={target}>
        <ArrowLeft className="size-4" />
        뒤로
      </Link>
    </Button>
  )
}
