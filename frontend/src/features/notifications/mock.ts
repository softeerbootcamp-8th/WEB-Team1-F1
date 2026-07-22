import type { AppNotification } from '@/types/domain'

const now = Date.now()

export const MOCK_NOTIFICATIONS: AppNotification[] = [
  {
    id: 1,
    type: 'WON',
    title: '낙찰되었습니다 🎉',
    body: '쏘렌토 하이브리드 시그니처를 38,500,000원에 낙찰받았습니다.',
    createdAt: new Date(now - 4 * 60_000).toISOString(),
    read: false,
    link: '/mypage',
  },
  {
    id: 2,
    type: 'EVAL_APPROVED',
    title: '평가사 방문이 승인되었습니다',
    body: '더 뉴 K5 매물의 평가 방문이 승인되었어요. 게시글을 작성해 경매를 열어보세요.',
    createdAt: new Date(now - 2 * 3600_000).toISOString(),
    read: false,
    link: '/sell',
  },
  {
    id: 3,
    type: 'DEAL_UPDATED',
    title: '거래 상태가 변경되었습니다',
    body: 'EV6 거래가 "배송중"으로 업데이트되었습니다.',
    createdAt: new Date(now - 26 * 3600_000).toISOString(),
    read: true,
    link: '/mypage',
  },
]
