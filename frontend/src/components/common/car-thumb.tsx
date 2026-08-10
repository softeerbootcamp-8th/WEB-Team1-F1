import { useState } from 'react'
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
 * 차량 썸네일. 주소가 없거나 불러오지 못하면 브랜드 톤(실버 그라데이션)의
 * 플레이스홀더로 폴백해 깨진 이미지를 방지한다.
 */
export function CarThumb({ src, alt, className, loading = 'lazy' }: CarThumbProps) {
  // 주소가 있어도 그 자원이 사라졌을 수 있다. 저장소를 옮기거나 객체가 지워지면
  // 화면에 깨진 이미지 아이콘이 남는데, 그건 차가 없다는 뜻으로 읽혀 오해를 만든다
  const [failed, setFailed] = useState(false)

  if (src && !failed) {
    return (
      <img
        src={src}
        alt={alt}
        loading={loading}
        decoding="async"
        onError={() => setFailed(true)}
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
