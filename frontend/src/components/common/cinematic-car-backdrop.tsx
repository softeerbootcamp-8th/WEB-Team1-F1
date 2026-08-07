import { cn } from '@/lib/utils'

type CinematicCarBackdropProps = {
  variant?: 'home' | 'auth'
  className?: string
  imageClassName?: string
  sizes?: string
}

/** 화면 비율에 맞춰 AI로 확장한 가로·세로 배경을 선택한다. */
export function CinematicCarBackdrop({
  variant = 'home',
  className,
  imageClassName,
  sizes = '100vw',
}: CinematicCarBackdropProps) {
  const tallImageMedia =
    variant === 'home' ? '(max-width: 1023px)' : '(min-width: 1024px)'

  return (
    <picture
      aria-hidden="true"
      className={cn('absolute inset-0 overflow-hidden bg-[#080a0b]', className)}
    >
      <source
        media={tallImageMedia}
        srcSet="/race-hero-tall-800.webp 800w, /race-hero-tall-1122.webp 1122w"
        sizes={sizes}
        type="image/webp"
      />
      <img
        src="/race-hero-wide-1280.webp"
        srcSet="/race-hero-wide-1280.webp 1280w, /race-hero-wide-1774.webp 1774w"
        sizes={sizes}
        alt=""
        className={cn(
          'absolute inset-0 size-full object-cover',
          imageClassName,
        )}
        decoding="async"
        fetchPriority="high"
      />
    </picture>
  )
}
