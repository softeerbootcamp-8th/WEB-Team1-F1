import type { AuctionCard, Bid } from '@/types/domain'

/**
 * 개발용 목업 데이터. 백엔드 연동 시 src/api/generated 훅으로 대체.
 * 시각은 now 기준 상대값으로 생성해 상태(진행/예정/종료)가 항상 자연스럽게 보이도록 한다.
 */
const now = Date.now()
const min = 60_000
const hour = 60 * min
const iso = (offsetMs: number) => new Date(now + offsetMs).toISOString()

export const MOCK_AUCTIONS: AuctionCard[] = [
  {
    id: 1,
    title: '무사고 · 1인신조 · 정비이력 완비',
    thumbnailUrl: '',
    car: {
      name: '더 뉴 K5 2.0 가솔린',
      year: 2022,
      mileageKm: 31200,
      fuel: '가솔린',
      evaluation: {
        exteriorFrame: '무사고',
        accidentHistory: '보험 이력 없음',
        keyOptions: '파노라마 선루프 · HUD',
        grade: 'A',
      },
    },
    status: 'LIVE',
    startPrice: 18_000_000,
    currentPrice: 21_250_000,
    bidCount: 24,
    startAt: iso(-40 * min),
    endAt: iso(3 * min + 12_000),
  },
  {
    id: 2,
    title: '풀옵션 · 파노라마 선루프',
    thumbnailUrl: '',
    car: {
      name: '쏘렌토 하이브리드 시그니처',
      year: 2023,
      mileageKm: 18450,
      fuel: '하이브리드',
      evaluation: {
        exteriorFrame: '단순 교환 1건',
        accidentHistory: '주요 골격 이상 없음',
        keyOptions: '드라이브 와이즈 · HUD',
        grade: 'A',
      },
    },
    status: 'LIVE',
    startPrice: 32_000_000,
    currentPrice: 38_500_000,
    bidCount: 41,
    startAt: iso(-25 * min),
    endAt: iso(11 * min),
  },
  {
    id: 3,
    title: '전기차 · 배터리 진단 우수',
    thumbnailUrl: '',
    car: {
      name: 'EV6 롱레인지 GT-Line',
      year: 2022,
      mileageKm: 27800,
      fuel: '전기',
      evaluation: {
        exteriorFrame: '무사고',
        accidentHistory: '보험 이력 없음',
        keyOptions: '배터리 진단 우수',
        grade: 'A',
      },
    },
    status: 'SCHEDULED',
    startPrice: 34_000_000,
    currentPrice: 34_000_000,
    bidCount: 0,
    startAt: iso(28 * min),
    endAt: iso(28 * min + hour),
  },
  {
    id: 4,
    title: '법인 리스반납 · 실내 A급',
    thumbnailUrl: '',
    car: {
      name: '카니발 4세대 노블레스',
      year: 2021,
      mileageKm: 62100,
      fuel: '디젤',
      evaluation: {
        exteriorFrame: '외판 교환 1건',
        accidentHistory: '주요 골격 이상 없음',
        keyOptions: '스마트 크루즈 · 통풍 시트',
        grade: 'B+',
      },
    },
    status: 'SCHEDULED',
    startPrice: 27_500_000,
    currentPrice: 27_500_000,
    bidCount: 0,
    startAt: iso(2 * hour),
    endAt: iso(3 * hour),
  },
  {
    id: 5,
    title: '단거리 위주 · 하부 깨끗',
    thumbnailUrl: '',
    car: {
      name: '스포티지 NQ5 프레스티지',
      year: 2023,
      mileageKm: 14200,
      fuel: '가솔린',
      evaluation: {
        exteriorFrame: '무사고',
        accidentHistory: '보험 이력 없음',
        keyOptions: '서라운드 뷰 · 스마트 크루즈',
        grade: 'A',
      },
    },
    status: 'ENDED',
    startPrice: 25_000_000,
    currentPrice: 29_750_000,
    bidCount: 33,
    startAt: iso(-3 * hour),
    endAt: iso(-30 * min),
  },
  {
    id: 6,
    title: '오너 직접등록 · 신차급',
    thumbnailUrl: '',
    car: {
      name: 'K8 3.5 가솔린 시그니처',
      year: 2022,
      mileageKm: 22600,
      fuel: '가솔린',
      evaluation: {
        exteriorFrame: '무사고',
        accidentHistory: '보험 이력 없음',
        keyOptions: '메리디안 사운드 · HUD',
        grade: 'A',
      },
    },
    status: 'ENDED',
    startPrice: 30_000_000,
    currentPrice: 33_250_000,
    bidCount: 19,
    startAt: iso(-5 * hour),
    endAt: iso(-2 * hour),
  },
]

export function getAuctionById(id: number): AuctionCard | undefined {
  return MOCK_AUCTIONS.find((a) => a.id === id)
}

/** 호가창 목업 — 최신순 */
export function mockBids(auction: AuctionCard): Bid[] {
  if (auction.bidCount === 0) return []
  const names = ['김민준', '이서연', '박도현', '최지우', '정하윤', '강시우', '조은우']
  const step = 250_000
  const rows: Bid[] = []
  let price = auction.currentPrice
  const count = Math.min(auction.bidCount, 20)
  for (let i = 0; i < count; i++) {
    rows.push({
      id: auction.id * 1000 + i,
      bidderNickname: names[i % names.length],
      bidderRole: i % 3 === 0 ? 'USER' : 'DEALER',
      amount: price,
      createdAt: new Date(now - i * 47_000).toISOString(),
      isMine: i === 1,
    })
    price -= step + (i % 3) * 50_000
  }
  return rows
}
