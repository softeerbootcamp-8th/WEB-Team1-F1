import Axios from 'axios'
import type { AxiosError, AxiosRequestConfig } from 'axios'

// orval mutator가 사용할 프로젝트 공용 axios 인스턴스
// 인증이 HttpOnly 쿠키 세션이라 withCredentials 없이는 Set-Cookie/쿠키 전송이 모두 안 된다
export const axiosInstance = Axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  withCredentials: true,
})

/**
 * orval이 생성한 API 함수들이 호출하는 커스텀 인스턴스.
 * react-query 훅이 넘겨주는 AbortSignal로 요청 취소를 지원한다.
 */
export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  const source = Axios.CancelToken.source()
  const promise = axiosInstance({
    ...config,
    ...options,
    cancelToken: source.token,
  }).then(({ data }) => data)

  // react-query가 쿼리 취소 시 호출할 수 있도록 cancel 메서드를 붙인다.
  // @ts-expect-error orval mutator 규약상 promise에 cancel을 부착한다.
  promise.cancel = () => {
    source.cancel('Query was cancelled')
  }

  return promise
}

export type ErrorType<Error> = AxiosError<Error>
export type BodyType<BodyData> = BodyData

interface ProblemDetailBody {
  detail?: string
  errors?: { field: string; message: string }[]
}

/** 백엔드 ProblemDetail(RFC 9457) 응답에서 사용자에게 보여줄 메시지를 뽑는다. */
export function getErrorMessage(error: unknown, fallback: string): string {
  const body = (error as AxiosError<ProblemDetailBody> | undefined)?.response?.data
  return body?.errors?.[0]?.message ?? body?.detail ?? fallback
}

export default customInstance
