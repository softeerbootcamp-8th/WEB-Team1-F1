import { describe, expect, it } from 'vitest'

import { openingOutcomeOf } from './opening-outcome'

describe('openingOutcomeOf', () => {
  it('안내를 받는 사이 방이 열렸으면 방으로 들어간다', () => {
    expect(openingOutcomeOf('ROOM_ALREADY_OPEN')).toBe('ENTER_ROOM')
  })

  // 끝난 방에 들어가 보면 서버가 또 거절한다, 그 왕복을 안 돌기 위해 여기서 결과로 보낸다
  it('안내를 받는 사이 경매가 끝났으면 결과로 넘어간다', () => {
    expect(openingOutcomeOf('ROOM_ALREADY_CLOSED')).toBe('RESULT')
  })

  it('그 밖의 사유는 오류로 둔다', () => {
    expect(openingOutcomeOf('AUCTION_ROOM_NOT_FOUND')).toBe('BROKEN')
    expect(openingOutcomeOf(null)).toBe('BROKEN')
  })
})
