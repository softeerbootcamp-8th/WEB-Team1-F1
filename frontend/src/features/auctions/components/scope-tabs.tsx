import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { AuctionListScope } from '@/features/auctions/types'

// 상태 필터에도 "전체"가 있어 같은 이름을 쓰지 않는다. 한 화면에서 가리키는 축이 다르다.
const SCOPES: { value: AuctionListScope; label: string }[] = [
  { value: 'ALL', label: '모든 경매' },
  { value: 'MINE', label: '나의 경매' },
]

interface ScopeTabsProps {
  value: AuctionListScope
  onChange: (scope: AuctionListScope) => void
}

/**
 * 전체 / 나의 경매 전환. 화면 폭을 반씩 꽉 채우고 선택된 쪽만 밑줄로 표시한다.
 * 색은 프로젝트 무채색 토큰만 쓴다 — 강조는 채도가 아니라 대비(foreground)로 준다.
 */
export function ScopeTabs({ value, onChange }: ScopeTabsProps) {
  return (
    <Tabs
      value={value}
      onValueChange={(next) => onChange(next as AuctionListScope)}
      className="w-full"
    >
      <TabsList
        aria-label="경매 범위 선택"
        className="bg-muted/40 grid h-14 w-full grid-cols-2 rounded-lg p-0"
      >
        {SCOPES.map((scope) => (
          <TabsTrigger
            key={scope.value}
            value={scope.value}
            className="data-[state=active]:bg-background data-[state=active]:border-b-foreground h-14 rounded-none border-b-2 border-transparent text-base first:rounded-tl-lg last:rounded-tr-lg data-[state=active]:font-semibold data-[state=active]:shadow-none"
          >
            {scope.label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )
}
