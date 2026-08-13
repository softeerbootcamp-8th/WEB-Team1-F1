import { Badge } from '@/components/ui/badge'
import { VEHICLE_KEYWORD_LABEL, type VehicleKeyword } from '../types'

/**
 * 차량 상태 키워드는 장식용 색상 태그가 아니라 진단 결과의 일부다.
 * 키워드마다 다른 색을 주지 않고 따뜻한 무채색 면으로 통일해, 문구는 분리하되
 * 의미가 없는 좋음·나쁨 색상 판정은 만들지 않는다.
 */
export function VehicleKeywordBadge({ keyword }: { keyword: VehicleKeyword }) {
  return (
    <Badge
      variant="outline"
      className="rounded-md border-[#dedbd2] bg-[#f3f1eb] px-2.5 py-1 text-[0.8rem] font-medium text-[#4f4c45] shadow-none dark:border-white/12 dark:bg-white/8 dark:text-white/80"
    >
      {VEHICLE_KEYWORD_LABEL[keyword]}
    </Badge>
  )
}
