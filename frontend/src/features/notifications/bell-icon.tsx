/** 헤더 알림 벨. 돔 + 아래 가로선 + 떨어진 추 세 획으로만 그린 형태다. */
export function BellIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden
    >
      <path d="M6 17V8a6 6 0 0 1 12 0v9" />
      <path d="M3 17h18" />
      <path d="M10.25 21h3.5" />
    </svg>
  )
}
