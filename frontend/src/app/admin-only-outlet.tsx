import { Navigate, Outlet } from 'react-router-dom'

import type { UserRole } from '@/types/domain'

/** 운영 화면은 관리자만 통과시킨다. 서버도 /api/admin/** 를 경로 자체로 다시 막는다. */
export function AdminOnlyOutlet({ role }: { role: UserRole | null }) {
  return role === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />
}
