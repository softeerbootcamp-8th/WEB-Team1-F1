import { axiosInstance } from '@/lib/axios'
import type { DealerApplicationStatus } from '@/features/admin/types'
import type { SelfSignUpRole, User } from '@/types/domain'

/**
 * /api/auth, /api/users 직접 호출. 세션은 HttpOnly 쿠키(RACE_SESSION)라
 * 응답 본문엔 토큰이 없고, axiosInstance의 withCredentials가 쿠키 송수신을 담당한다.
 * 스웨거 스키마가 안정되면 pnpm api:gen으로 orval 훅으로 교체한다.
 */

export interface LoginPayload {
  username: string
  password: string
}

export interface SignUpPayload {
  username: string
  email: string
  password: string
  realName: string
  phone: string
  role: SelfSignUpRole
  dealerLicenseKey?: string
}

export async function loginRequest(payload: LoginPayload): Promise<User> {
  const { data } = await axiosInstance.post<User>('/api/auth/login', payload)
  return data
}

export async function logoutRequest(): Promise<void> {
  await axiosInstance.post('/api/auth/logout')
}

export async function fetchMe(): Promise<User> {
  const { data } = await axiosInstance.get<User>('/api/auth/me')
  return data
}

/**
 * 회원가입 응답. 딜러로 신청해도 만들어지는 회원은 일반 회원이라 role은 GENERAL로 온다 —
 * 딜러 자격은 관리자가 승인할 때 붙는다. dealerApplicationStatus가 그 요청이 심사로 접수됐음을
 * 알려주고, 이 값이 없으면 딜러 선택이 무시된 것과 구분할 수 없다.
 */
export interface SignUpResult extends User {
  dealerApplicationStatus: DealerApplicationStatus | null
}

export async function signUpRequest(payload: SignUpPayload): Promise<SignUpResult> {
  const { data } = await axiosInstance.post<SignUpResult>('/api/users', payload)
  return data
}
