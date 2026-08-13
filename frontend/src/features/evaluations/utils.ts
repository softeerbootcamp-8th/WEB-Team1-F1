import { isAxiosError } from 'axios'
import type { EvaluationAuctionStatus, EvaluationStatus } from './types'

interface EvaluationProblemDetail {
  code?: string
}

export function getEvaluationErrorCode(error: unknown): string | undefined {
  if (!isAxiosError<EvaluationProblemDetail>(error)) return undefined
  return error.response?.data?.code
}

export function formatVisitDate(value: string): string {
  return new Date(`${value}T00:00:00`).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })
}

export function formatPhone(value: string): string {
  if (value.length === 11) return value.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3')
  if (value.length === 10) return value.replace(/(\d{3})(\d{3})(\d{4})/, '$1-$2-$3')
  return value
}

export function getEvaluationStatusMeta(
  status: EvaluationStatus,
  assigned: boolean,
  viewer: 'seller' | 'evaluator' = 'seller',
) {
  if (status === 'APPROVED') {
    return {
      label: viewer === 'evaluator' ? '승인 처리' : '진단 완료',
      className: 'bg-success/10 text-success border-success/20',
    }
  }
  if (status === 'REJECTED') {
    return {
      label: viewer === 'evaluator' ? '반려 처리' : '반려',
      className: 'bg-destructive/10 text-destructive border-destructive/20',
    }
  }
  if (viewer === 'evaluator') {
    return { label: '평가 수락', className: 'bg-deal-active/10 text-deal-active border-deal-active/20' }
  }
  if (assigned) {
    return { label: '평가사 배정됨', className: 'bg-deal-active/10 text-deal-active border-deal-active/20' }
  }
  return { label: '접수됨 · 배정 대기', className: 'bg-warning/10 text-warning border-warning/20' }
}

export function getAuctionStatusMeta(status: EvaluationAuctionStatus) {
  if (status === 'SCHEDULED') {
    return { label: '경매 예정', className: 'bg-warning/10 text-warning border-warning/20' }
  }
  if (status === 'IN_PROGRESS') {
    return { label: '경매 진행 중', className: 'bg-primary/10 text-primary border-primary/20' }
  }
  if (status === 'ENDED') {
    return { label: '낙찰 완료', className: 'bg-success/10 text-success border-success/20' }
  }
  return { label: '유찰', className: 'bg-muted text-muted-foreground border-border' }
}

/**
 * 이 차량을 지금 (재)출품할 수 있는지. 서버 `AuctionService.ACTIVE_STATUSES` 와 같은 기준이다 —
 * 예정 · 진행 중 · 낙찰 종료는 막고, 유찰만 재출품을 연다.
 *
 * 상태는 상세 응답이 아니라 신청 목록에서 온다. 출품 버튼과 등록 화면 가드가 이 함수 하나를
 * 같이 써야 두 곳의 기준이 갈라지지 않는다.
 *
 * 상태를 모를 때(목록을 아직 못 읽었거나 조회에 실패했을 때)도 true 다. 화면은 최종 방어선이
 * 아니라 헛걸음을 줄이는 자리이고, 여기서 막으면 목록 조회 실패가 곧 출품 불가가 된다.
 * 실제 중복은 서버가 409 AUCTION_ALREADY_EXISTS 로 돌려보낸다.
 */
export function canRegisterAuction(
  status: EvaluationAuctionStatus | null | undefined,
): boolean {
  return status == null || status === 'FAILED'
}

/** 출품을 막는 이유. 판매자가 "왜 등록 버튼이 없는가"를 화면에서 바로 알 수 있어야 한다. */
export function getAuctionBlockReason(status: EvaluationAuctionStatus): string {
  if (status === 'SCHEDULED') {
    return '경매 시작을 기다리는 중입니다. 시작 전이라면 경매 목록의 "나의 경매"에서 시작가와 시각을 수정할 수 있어요.'
  }
  if (status === 'IN_PROGRESS') {
    return '경매가 진행 중입니다. 마감되면 결과를 확인할 수 있어요.'
  }
  return '낙찰이 끝난 차량입니다.'
}
