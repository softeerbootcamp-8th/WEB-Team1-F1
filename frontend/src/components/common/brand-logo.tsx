import { cn } from '@/lib/utils'

type BrandLogoProps = {
  variant?: 'black' | 'white'
  className?: string
}

export function BrandLogo({
  variant = 'black',
  className,
}: BrandLogoProps) {
  return (
    <img
      src={variant === 'black' ? '/race-black.svg' : '/race-white.svg'}
      alt="RACE"
      className={cn('block w-auto object-contain', className)}
    />
  )
}
