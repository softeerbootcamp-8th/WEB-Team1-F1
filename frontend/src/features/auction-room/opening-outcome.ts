/**
 * 개장 안내를 받지 못한 뒤에 화면이 갈 곳.
 * 방이 그새 열렸으면 방으로 들어가고, 그새 끝났으면 결과로 넘어간다.
 */
export type OpeningOutcome = 'ENTER_ROOM' | 'RESULT' | 'BROKEN'

/** 사유는 서버가 코드로 알려준다, 화면이 시각을 보고 스스로 정하지 않는다 */
export function openingOutcomeOf(errorCode: string | null): OpeningOutcome {
  if (errorCode === 'ROOM_ALREADY_OPEN') return 'ENTER_ROOM'
  if (errorCode === 'ROOM_ALREADY_CLOSED') return 'RESULT'

  return 'BROKEN'
}
