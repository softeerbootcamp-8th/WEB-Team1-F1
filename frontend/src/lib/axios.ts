import Axios from 'axios'
import type { AxiosError, AxiosRequestConfig } from 'axios'

// orval mutator가 사용할 프로젝트 공용 axios 인스턴스
// 인증이 HttpOnly 쿠키 세션이라 withCredentials 없이는 Set-Cookie/쿠키 전송이 모두 안 된다
//
// baseURL은 Vite가 빌드 시점에 문자열로 박아 넣는다. 값이 있으면 그대로 절대 오리진이 되고,
// 운영은 .env.production의 https://api.f1race.site를 쓴다. www 프론트와 API는 오리진이 달라
// 백엔드 CORS 허용과 withCredentials가 함께 필요하다.
// 값이 없을 때의 대체값만 빈 문자열(=프론트 오리진의 상대 경로)이다. 여기에 로컬 주소를 두면
// 값을 빠뜨린 채 빌드했을 때 방문자의 8080을 때리는 번들이 조용히 배포되므로, 상대 경로로 둬서
// www.f1race.site/api/*를 향한 404로 로컬·스테이징에서 먼저 깨지게 한다.
export const axiosInstance = Axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
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
  code?: string
  errors?: { field: string; message: string }[]
}

/** 백엔드 ProblemDetail(RFC 9457) 응답에서 사용자에게 보여줄 메시지를 뽑는다. */
export function getErrorMessage(error: unknown, fallback: string): string {
  const body = (error as AxiosError<ProblemDetailBody> | undefined)?.response?.data
  return body?.errors?.[0]?.message ?? body?.detail ?? fallback
}

/**
 * 실패 응답의 도메인 에러 코드(ex. ROOM_ALREADY_CLOSED). 화면이 사유별로 갈라져야 할 때 쓴다.
 * 문구(detail)로 분기하면 서버가 문구를 다듬는 순간 화면이 조용히 틀어진다.
 * 스프링 내장 예외에는 코드가 없어 undefined 가 나온다.
 */
export function getErrorCode(error: unknown): string | undefined {
  return (error as AxiosError<ProblemDetailBody> | undefined)?.response?.data?.code
}

/**
 * 실패 응답의 상태 코드. 응답을 못 받았으면(네트워크 오류) undefined 다.
 * 인증처럼 HTTP가 뜻을 이미 정한 실패는 도메인 코드가 아니라 이 값으로 가른다.
 */
export function getErrorStatus(error: unknown): number | undefined {
  return (error as AxiosError | undefined)?.response?.status
}

export default customInstance
