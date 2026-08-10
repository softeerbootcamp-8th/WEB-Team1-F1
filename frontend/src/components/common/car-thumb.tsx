import { Car } from 'lucide-react'
import { cn } from '@/lib/utils'

interface CarThumbProps {
  /** 실제 이미지 URL. 없으면 실버 그라데이션 플레이스홀더를 렌더. */
  src?: string
  alt: string
  className?: string
  /** 캐러셀처럼 이미 화면에 있는 자리는 eager, 목록 카드는 기본 lazy */
  loading?: 'lazy' | 'eager'
}

/**
 * 차량 썸네일. 이미지가 없을 때 브랜드 톤(실버 그라데이션)의
 * 플레이스홀더로 폴백해 깨진 이미지를 방지한다.
 */
export function CarThumb({ src, alt, className, loading = 'lazy' }: CarThumbProps) {
  if (src) {
    return (
      <img
        src={src}
        alt={alt}
        loading={loading}
        decoding="async"
        className={cn('h-full w-full object-cover', className)}
      />
    )
  }
  return (
    <div
      role="img"
      aria-label={alt}
      className={cn(
        'flex h-full w-full items-center justify-center bg-gradient-to-br from-muted via-secondary to-muted',
        className,
      )}
    >
      <Car className="text-muted-foreground/40 size-1/4" strokeWidth={1} />
    </div>
  )
}
