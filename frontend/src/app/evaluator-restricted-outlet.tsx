import { Navigate, Outlet } from 'react-router-dom'

import type { UserRole } from '@/types/domain'

/** 평가사에게 판매자 전용 화면을 숨기는 UX 가드이며, 실제 데이터 인가는 서버가 맡는다. */
export function EvaluatorRestrictedOutlet({ role }: { role: UserRole | null }) {
  return role === 'EVALUATOR' ? <Navigate to="/" replace /> : <Outlet />
}
