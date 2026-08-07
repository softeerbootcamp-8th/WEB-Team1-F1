import { Store, User } from 'lucide-react'

import { cn } from '@/lib/utils'
import type { SelfSignUpRole } from '@/types/domain'

const OPTIONS: {
  value: SelfSignUpRole
  label: string
  icon: typeof User
}[] = [
  { value: 'GENERAL', label: '개인', icon: User },
  { value: 'DEALER', label: '딜러', icon: Store },
]

/**
 * 계정 유형(개인/딜러) 선택. 두 갈래뿐이고 라벨만으로 뜻이 통해
 * 설명 없이 한 줄짜리 선택지로 둔다.
 * 유형은 신원 구분일 뿐, 구매·판매 기능은 양쪽 모두 동일하게 가능하다.
 */
export function RoleSelect({
  value,
  onChange,
}: {
  value: SelfSignUpRole
  onChange: (role: SelfSignUpRole) => void
}) {
  return (
    <div role="radiogroup" aria-label="역할 선택" className="grid grid-cols-2 gap-2">
      {OPTIONS.map((opt) => {
        const active = value === opt.value
        const Icon = opt.icon
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(opt.value)}
            className={cn(
              'flex items-center justify-center gap-2 rounded-lg border px-3 py-2.5 text-sm transition-colors',
              active
                ? 'border-primary ring-primary/20 bg-accent ring-2'
                : 'hover:bg-accent/50',
            )}
          >
            <Icon className="size-4" />
            <span className="font-medium">{opt.label}</span>
          </button>
        )
      })}
    </div>
  )
}
