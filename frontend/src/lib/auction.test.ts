import { describe, expect, it } from 'vitest'

import type { BidIncrementBand } from '@/features/auction-room/types'
import type { AuctionListCard, RoomPhase } from '@/features/auctions/types'

import {
  arrangeCards,
  canDeleteAuction,
  canEditAuction,
  incrementForPrice,
  listGroupAt,
  roomPhaseToBadgeStatus,
} from './auction'

// 서버 시드와 같은 모양. 값 자체는 계약이 아니라 구간을 고르는 규칙만 본다
const bands: BidIncrementBand[] = [
  { minPrice: 0, increment: 100_000 },
  { minPrice: 10_000_000, increment: 500_000 },
  { minPrice: 50_000_000, increment: 1_000_000 },
]

describe('incrementForPrice', () => {
  it('가격이 속한 구간의 상승가를 돌려준다', () => {
    expect(incrementForPrice(30_000_000, bands)).toBe(500_000)
  })

  it('구간 하한과 정확히 같은 가격은 그 구간에 속한다', () => {
    expect(incrementForPrice(10_000_000, bands)).toBe(500_000)
    expect(incrementForPrice(9_999_999, bands)).toBe(100_000)
  })

  it('마지막 구간 위쪽은 모두 마지막 구간이다', () => {
    expect(incrementForPrice(9_999_999_999, bands)).toBe(1_000_000)
  })

  it('구간이 순서 없이 들어와도 올바른 구간을 고른다', () => {
    const shuffled = [bands[2], bands[0], bands[1]]
    expect(incrementForPrice(30_000_000, shuffled)).toBe(500_000)
  })

  // 서버는 담당 구간이 없으면 중단한다, 근거 없는 상승가로 입찰이 성립해서는 안 되기 때문이다
  // 화면도 같은 판단을 해야 한다, 0을 돌려주면 "올리지 않아도 되는 입찰"을 안내하게 된다
  it('담당 구간이 없으면 상승가를 정하지 않는다', () => {
    expect(incrementForPrice(-1, bands)).toBeNull()
  })

  it('구간표가 비어 있으면 상승가를 정하지 않는다', () => {
    expect(incrementForPrice(30_000_000, [])).toBeNull()
  })
})

describe('뱃지 단계', () => {
  // 시작 전을 하나로 뭉치면 "지금 들어갈 수 있는 경매"를 목록에서 골라낼 수 없다.
  // 방 개설은 시작 30분 전이라 이 구분이 사라지면 대기실이 열린 짧은 창을 놓친다
  it('방이 열리기 전과 열린 뒤를 나눠서 보여준다', () => {
    expect(roomPhaseToBadgeStatus('NOT_OPEN')).toBe('NOT_OPEN')
    expect(roomPhaseToBadgeStatus('WAITING')).toBe('WAITING')
  })

  // 결과 확인 구간은 이미 입찰이 끝난 뒤다. 따로 보여주면 아직 참여할 수 있는 것처럼 읽힌다
  it('마감 뒤 두 단계는 종료 하나로 묶는다', () => {
    expect(roomPhaseToBadgeStatus('RESULT')).toBe('ENDED')
    expect(roomPhaseToBadgeStatus('CLOSED')).toBe('ENDED')
  })

  it('진행중은 그대로 진행중이다', () => {
    expect(roomPhaseToBadgeStatus('LIVE')).toBe('LIVE')
  })
})

describe('경매 수정과 삭제 가능 여부', () => {
  const phases: RoomPhase[] = ['NOT_OPEN', 'WAITING', 'LIVE', 'RESULT', 'CLOSED']

  it('아직 열리지 않은 경매만 수정할 수 있다', () => {
    const editable = phases.filter(canEditAuction)
    expect(editable).toEqual(['NOT_OPEN'])
  })

  it('끝난 경매만 삭제할 수 있다', () => {
    const deletable = phases.filter(canDeleteAuction)
    expect(deletable).toEqual(['RESULT', 'CLOSED'])
  })
})

// ================= 시각으로 다시 판정하는 그룹과 자리 =================

/** 서버가 주는 것과 같은 오프셋 없는 KST 문자열 */
function at(time: string): number {
  return new Date(`2026-08-03T${time}`).getTime()
}

// phase 는 서버가 조회 시각에 계산해 둔 값이다. 시간이 지나면 낡으므로 아래 함수들은 이 값을
// 보지 않는다. 낡았다는 것을 드러내려고 픽스처에서는 전부 LIVE 로 채워 둔다
function card(auctionId: number, startAt: string, endAt: string): AuctionListCard {
  return {
    auctionId,
    phase: 'LIVE',
    thumbnailUrl: null,
    model: `차 ${auctionId}`,
    modelYear: 2022,
    mileage: 30_000,
    startPrice: 10_000_000,
    currentPrice: 10_000_000,
    openAt: '2026-08-03T00:00:00',
    startAt: `2026-08-03T${startAt}`,
    endAt: `2026-08-03T${endAt}`,
    connectedCount: 0,
  }
}

const ids = (cards: AuctionListCard[]) => cards.map((it) => it.auctionId)

// 12:00 기준으로 서버가 내려주는 한 페이지. 진행중은 마감 임박순, 예정은 시작 임박순,
// 종료는 최근 마감순이고 그 셋을 이어 붙인 것이 응답의 순서다
const page: AuctionListCard[] = [
  card(1, '11:50:00', '12:10:00'),
  card(2, '11:55:00', '12:15:00'),
  card(3, '12:05:00', '12:25:00'),
  card(4, '12:30:00', '12:50:00'),
  card(5, '11:35:00', '11:55:00'),
  card(6, '11:10:00', '11:30:00'),
]

describe('listGroupAt', () => {
  it('경계 시각은 서버와 같은 쪽에 붙는다', () => {
    // 서버 쿼리가 start_time <= now 이고 now < current_end_time 인 것을 진행중으로 본다
    expect(listGroupAt(card(1, '12:00:00', '12:20:00'), at('12:00:00'))).toBe('LIVE')
    expect(listGroupAt(card(1, '12:00:00', '12:20:00'), at('11:59:59'))).toBe('PENDING')
    expect(listGroupAt(card(1, '11:40:00', '12:00:00'), at('12:00:00'))).toBe('ENDED')
  })
})

describe('arrangeCards', () => {
  it('서버가 준 순서를 그 시각 그대로 다시 배치하면 순서가 그대로다', () => {
    // 서버의 정렬과 어긋나지 않는다는 계약. 서버 order by 가 바뀌면 여기서 깨진다
    expect(ids(arrangeCards(page, at('12:00:00'), null))).toEqual([1, 2, 3, 4, 5, 6])
  })

  it('마감이 지난 카드가 종료 무리 맨 앞으로 간다', () => {
    // 1번이 12:10 에 마감된다. 종료는 최근 마감순이라 그 무리의 맨 앞이 제자리다
    expect(ids(arrangeCards(page, at('12:10:00'), null))).toEqual([2, 3, 4, 1, 5, 6])
  })

  it('시작한 카드는 예정에서 빠지고 진행중으로 분류된다', () => {
    // 전체 목록에서는 자리가 안 바뀐다. 예정 무리의 맨 앞이 곧 진행중 무리의 맨 뒤라서다
    // 그래서 이 전이의 관찰 가능한 결과는 순서가 아니라 그룹이고, 필터를 켜야 드러난다
    expect(ids(arrangeCards(page, at('12:05:00'), null))).toEqual([1, 2, 3, 4, 5, 6])
    expect(ids(arrangeCards(page, at('12:05:00'), 'PENDING'))).toEqual([4])
    expect(ids(arrangeCards(page, at('12:05:00'), 'LIVE'))).toEqual([1, 2, 3])
  })

  it('필터가 켜지면 그 그룹이 아닌 카드는 빠진다', () => {
    // 12:10 이면 1번은 마감됐고 3번은 이미 시작했다, 진행중에 둘이 남는다
    expect(ids(arrangeCards(page, at('12:10:00'), 'LIVE'))).toEqual([2, 3])
    expect(ids(arrangeCards(page, at('12:10:00'), 'ENDED'))).toEqual([1, 5, 6])
  })
})
