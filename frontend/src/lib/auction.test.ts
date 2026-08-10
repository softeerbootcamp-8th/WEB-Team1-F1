import { describe, expect, it } from 'vitest'

import type { BidIncrementBand } from '@/features/auction-room/types'
import type { RoomPhase } from '@/features/auctions/types'

import {
  canDeleteAuction,
  canEditAuction,
  incrementForPrice,
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
