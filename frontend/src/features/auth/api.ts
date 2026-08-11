import { axiosInstance } from '@/lib/axios'
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

export async function signUpRequest(payload: SignUpPayload): Promise<User> {
  const { data } = await axiosInstance.post<User>('/api/users', payload)
  return data
}
