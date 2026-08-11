import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center justify-center rounded-md border px-2 py-0.5 text-xs font-medium w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none transition-colors overflow-hidden',
  {
    variants: {
      variant: {
        default:
          'border-transparent bg-primary text-primary-foreground [a&]:hover:bg-primary/90',
        secondary:
          'border-transparent bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90',
        destructive:
          'border-transparent bg-destructive text-destructive-foreground [a&]:hover:bg-destructive/90',
        outline: 'text-foreground [a&]:hover:bg-accent',
        // 경매 상태 전용 variants.
        // 카드 썸네일 위에 얹히므로 반투명 대신 배경색에 미리 섞어 불투명하게 쓴다.
        // 사진이 비쳐 글자가 묻히는 걸 막으면서, 단색 배경 위에서는 기존과 같은 톤이 나온다.
        live: 'border-transparent bg-[color-mix(in_oklab,var(--status-live)_12%,var(--background))] text-status-live',
        scheduled:
          'border-transparent bg-[color-mix(in_oklab,var(--status-scheduled)_12%,var(--background))] text-status-scheduled',
        waiting:
          'border-transparent bg-[color-mix(in_oklab,var(--status-waiting)_12%,var(--background))] text-status-waiting',
        ended: 'border-transparent bg-muted text-muted-foreground',
        warning:
          'border-transparent bg-closing-soon/15 text-closing-soon',
        success: 'border-transparent bg-success/12 text-success',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
)

function Badge({
  className,
  variant,
  asChild = false,
  ...props
}: React.ComponentProps<'span'> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : 'span'

  return (
    <Comp
      data-slot="badge"
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
