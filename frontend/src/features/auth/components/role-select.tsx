import { Store, User } from 'lucide-react'

import { cn } from '@/lib/utils'
import type { SelfSignUpRole } from '@/types/domain'

const OPTIONS: {
  value: SelfSignUpRole
  label: string
  desc: string
  icon: typeof User
}[] = [
  { value: 'GENERAL', label: '개인', desc: '개인 회원으로 이용', icon: User },
  { value: 'DEALER', label: '딜러', desc: '사업자 딜러로 이용', icon: Store },
]

/**
 * 계정 유형(개인/딜러) 선택 카드 그룹.
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
    <div role="radiogroup" aria-label="역할 선택" className="grid grid-cols-2 gap-3">
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
              'flex flex-col items-start gap-1 rounded-lg border p-4 text-left transition-colors',
              active
                ? 'border-primary ring-primary/20 bg-accent ring-2'
                : 'hover:bg-accent/50',
            )}
          >
            <Icon className="size-5" />
            <span className="mt-1 font-medium">{opt.label}</span>
            <span className="text-muted-foreground text-xs">{opt.desc}</span>
          </button>
        )
      })}
    </div>
  )
}
