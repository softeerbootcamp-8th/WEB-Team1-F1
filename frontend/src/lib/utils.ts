import { clsx } from 'clsx'
import type { ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * className 병합 헬퍼. 조건부 클래스(clsx) + Tailwind 충돌 해소(twMerge).
 * shadcn/ui 컴포넌트와 커스텀 컴포넌트 전반에서 사용한다.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
