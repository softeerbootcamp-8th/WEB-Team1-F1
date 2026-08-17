import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

export interface ScopeOption<T extends string> {
  value: T
  label: string
  /** 라벨 뒤에 붙는 건수. 목록을 통째로 받는 화면만 셀 수 있어 없을 수 있다 */
  count?: number
}

interface EvaluationScopeTabsProps<T extends string> {
  value: T
  options: ScopeOption<T>[]
  onChange: (scope: T) => void
  /** 스크린리더가 읽을 이 탭 묶음의 이름. 화면마다 무엇을 가르는 축인지 다르다 */
  label: string
}

/**
 * 목록의 범위를 바꾸는 탭. 평가사의 담당 목록과 판매자의 신청 내역이 같은 모양을 쓴다 —
 * 한 서비스 안에서 목록의 축을 바꾸는 조작이 화면마다 달라 보일 이유가 없고, 경매 목록의
 * ScopeTabs도 같은 모양이다.
 *
 * 담기는 값은 화면이 정한다. 여기서 상수로 들고 있으면 축이 다른 두 목록(평가사는 진단을
 * 썼는가, 판매자는 신경 쓸 것이 남았는가)이 한 파일에서 뒤섞인다.
 */
export function EvaluationScopeTabs<T extends string>({
  value,
  options,
  onChange,
  label,
}: EvaluationScopeTabsProps<T>) {
  return (
    <Tabs value={value} onValueChange={(next) => onChange(next as T)} className="w-full">
      <TabsList
        aria-label={label}
        className="bg-muted/40 grid h-14 w-full rounded-lg p-0"
        style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
      >
        {options.map((option) => (
          <TabsTrigger
            key={option.value}
            value={option.value}
            className="data-[state=active]:bg-background data-[state=active]:border-b-foreground h-14 rounded-none border-b-2 border-transparent text-base first:rounded-tl-lg last:rounded-tr-lg data-[state=active]:font-semibold data-[state=active]:shadow-none"
          >
            {option.label}
            {option.count !== undefined && (
              <span className="text-muted-foreground tabular ml-1.5 text-sm">{option.count}</span>
            )}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )
}
