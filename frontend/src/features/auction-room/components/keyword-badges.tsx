import { VehicleKeywordBadge } from '@/features/quote/components/vehicle-keyword-badge'
import type { VehicleKeyword } from '@/features/quote/types'

/**
 * 차량 제목 아래 키워드 줄. 목록 카드와 같은 자리에 두어 화면을 옮겨도 눈이 찾는 곳이 같다.
 * 진단 키워드가 없는 차량은 줄 자체를 그리지 않는다. 카드가 빈 줄을 남기는 것은 그리드 행
 * 높이를 맞추려는 것이고 제목 옆은 그리드가 아니다.
 */
export function KeywordBadges({ keywords }: { keywords: VehicleKeyword[] }) {
  if (keywords.length === 0) {
    return null
  }

  // 개수를 자르지 않는다, 카드가 앞 세 개만 보이는 것은 카드 폭이 좁아서다
  return (
    <div className="flex flex-wrap gap-1">
      {keywords.map((keyword) => (
        <VehicleKeywordBadge key={keyword} keyword={keyword} />
      ))}
    </div>
  )
}
