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

export function getEvaluationStatusMeta(status: EvaluationStatus, assigned: boolean) {
  if (status === 'APPROVED') {
    return { label: '진단 완료', className: 'bg-success/10 text-success border-success/20' }
  }
  if (status === 'REJECTED') {
    return { label: '반려', className: 'bg-destructive/10 text-destructive border-destructive/20' }
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
