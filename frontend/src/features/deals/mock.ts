import type { Deal } from '@/types/domain'

const now = Date.now()

export const MOCK_DEALS: Deal[] = [
  {
    id: 101,
    auctionId: 2,
    carName: '쏘렌토 하이브리드 시그니처',
    thumbnailUrl: '',
    finalPrice: 38_500_000,
    status: 'PENDING_SELLER',
    myRole: 'SELLER',
    counterpartNickname: '베스트딜러',
    updatedAt: new Date(now - 5 * 60_000).toISOString(),
  },
  {
    id: 102,
    auctionId: 3,
    carName: 'EV6 롱레인지 GT-Line',
    thumbnailUrl: '',
    finalPrice: 41_000_000,
    status: 'IN_TRANSIT',
    myRole: 'BUYER',
    counterpartNickname: '김판매',
    updatedAt: new Date(now - 26 * 3600_000).toISOString(),
  },
  {
    id: 103,
    auctionId: 5,
    carName: '스포티지 NQ5 프레스티지',
    thumbnailUrl: '',
    finalPrice: 29_750_000,
    status: 'COMPLETED',
    myRole: 'BUYER',
    counterpartNickname: '오토딜러',
    updatedAt: new Date(now - 3 * 24 * 3600_000).toISOString(),
  },
]
