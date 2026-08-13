import { cn } from '@/lib/utils'

interface SpecListProps {
  items: { label: string; value: string }[]
  className?: string
}

/** 라벨과 값을 줄로 세운 표, 라벨 폭을 고정해 값의 시작선을 맞춘다 */
export function SpecList({ items, className }: SpecListProps) {
  return (
    <dl className={cn('grid gap-x-10 gap-y-3 sm:grid-cols-2', className)}>
      {items.map((item) => (
        <div key={item.label} className="flex items-baseline gap-4">
          <dt className="text-muted-foreground w-20 shrink-0">{item.label}</dt>
          <dd className="tabular font-semibold">{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}
