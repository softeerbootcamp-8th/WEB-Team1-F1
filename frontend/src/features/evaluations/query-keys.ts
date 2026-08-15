import type { AssignableEvaluationSort } from './types'

export const ASSIGNABLE_EVALUATIONS_QUERY_KEY = ['evaluations', 'assignable'] as const

/**
 * 배정 대기 건수. 목록 키를 접두사로 삼는다.
 *
 * 새 방문견적 알림이 오면 목록 키를 무효화하는데(`use-notifications`), react-query가 접두사로
 * 일치를 보므로 이 키까지 함께 다시 읽힌다. 신청이 하나 늘면 목록과 건수가 같이 바뀌는 값이라
 * 한쪽만 낡는 편보다 낫다. 반대로 건수만 다시 읽고 싶을 때는 이 키를 그대로 쓰면 된다.
 */
export const ASSIGNABLE_EVALUATIONS_COUNT_QUERY_KEY = [
  ...ASSIGNABLE_EVALUATIONS_QUERY_KEY,
  'count',
] as const

/**
 * 정렬별 목록의 공통 접두사.
 *
 * 정렬마다 순서가 달라 캐시도 따로 쌓인다. 한 단계를 두는 것은 수락한 신청을 두 정렬 캐시에서
 * 한 번에 내리기 위해서다 — 이 접두사로 목록만 골라내면 모양이 다른 건수 캐시를 건드리지 않는다.
 */
export const ASSIGNABLE_EVALUATIONS_LIST_QUERY_KEY = [
  ...ASSIGNABLE_EVALUATIONS_QUERY_KEY,
  'list',
] as const

/** 한 정렬의 목록. 정렬이 키에 들어가지 않으면 순서가 다른 페이지 캐시를 그대로 재사용해 섞인다 */
export const assignableEvaluationsQueryKey = (sort: AssignableEvaluationSort) =>
  [...ASSIGNABLE_EVALUATIONS_LIST_QUERY_KEY, sort] as const

export const MY_ASSIGNMENTS_QUERY_KEY = ['evaluations', 'my-assignments'] as const

/** 담당 건수. 목록 키를 접두사로 삼아 목록이 무효화될 때 함께 다시 읽힌다 */
export const MY_ASSIGNMENTS_COUNT_QUERY_KEY = [
  ...MY_ASSIGNMENTS_QUERY_KEY,
  'count',
] as const
