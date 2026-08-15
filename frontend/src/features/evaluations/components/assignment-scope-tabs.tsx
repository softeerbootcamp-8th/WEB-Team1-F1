import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { EvaluationAssignmentScope } from '../types'

// "전체" 탭은 두지 않는다. 두 탭의 합이 곧 전체라 세 번째 선택지는 같은 것을 두 번 보여줄
// 뿐이고, 상태별 건수가 필요한 평가사 홈은 목록이 아니라 건수 조회를 쓴다.
const SCOPES: { value: EvaluationAssignmentScope; label: string }[] = [
  { value: 'ACTIVE', label: '진행 중' },
  { value: 'COMPLETED', label: '완료' },
]

interface AssignmentScopeTabsProps {
  value: EvaluationAssignmentScope
  onChange: (scope: EvaluationAssignmentScope) => void
}

/**
 * 진행 중 / 완료 전환. 경매 목록의 ScopeTabs와 같은 모양을 쓴다 — 한 서비스 안에서 목록의
 * 축을 바꾸는 조작이 화면마다 달라 보일 이유가 없다.
 */
export function AssignmentScopeTabs({ value, onChange }: AssignmentScopeTabsProps) {
  return (
    <Tabs
      value={value}
      onValueChange={(next) => onChange(next as EvaluationAssignmentScope)}
      className="w-full"
    >
      <TabsList
        aria-label="담당 목록 범위 선택"
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
