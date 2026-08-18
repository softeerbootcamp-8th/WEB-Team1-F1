/** 거래 조회 캐시의 공통 접두사. 알림 한 건으로 상세 캐시를 한꺼번에 내릴 때 이것을 쓴다 */
export const DEALS_QUERY_KEY = ['deals'] as const

/** 거래 한 건의 상세. 목록 접두사 아래에 두어 무효화 한 번이 열려 있는 상세를 함께 내린다 */
export const dealDetailQueryKey = (dealId: number) =>
  [...DEALS_QUERY_KEY, 'detail', dealId] as const
