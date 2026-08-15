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

export const MY_ASSIGNMENTS_QUERY_KEY = ['evaluations', 'my-assignments'] as const
