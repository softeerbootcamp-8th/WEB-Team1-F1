import { CalendarDays, CircleCheckBig, Clock3, MapPin } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { formatDateTime } from '@/lib/format'
import type { EvaluationSummary } from '../types'
import { formatVisitDate, getAuctionStatusMeta } from '../utils'
import { EvaluationProgress } from './evaluation-progress'

interface EvaluationSummaryCardProps {
  evaluation: EvaluationSummary
  action?: React.ReactNode
  layout?: 'card' | 'list'
  viewer?: 'seller' | 'evaluator'
}

export function EvaluationSummaryCard({
  evaluation,
  action,
  layout = 'card',
  viewer = 'seller',
}: EvaluationSummaryCardProps) {
  const auctionStatus = evaluation.auctionStatus
    ? viewer === 'evaluator'
      ? {
          label: '경매 등록됨',
          className: 'bg-primary/10 text-primary border-primary/20',
        }
      : getAuctionStatusMeta(evaluation.auctionStatus)
    : null

  const auctionBadge = auctionStatus ? (
    <Badge
      variant="outline"
      className={`rounded-full px-3 py-1 text-sm font-semibold ${auctionStatus.className}`}
    >
      {auctionStatus.label}
    </Badge>
  ) : null

  if (layout === 'list') {
    return (
      <Card className="gap-0 overflow-hidden border-border/80 py-0 shadow-sm transition-[border-color,box-shadow] hover:border-foreground/15 hover:shadow-md">
        <CardContent className="grid p-0 lg:grid-cols-[minmax(0,1fr)_13rem]">
          <div className="min-w-0 p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <h2 className="text-xl font-semibold">
                {MANUFACTURER_LABEL[evaluation.manufacturer]} {evaluation.model}
              </h2>
              {auctionBadge}
            </div>

            <p className="text-muted-foreground mt-2 text-sm">
              {evaluation.modelYear}년식 · {evaluation.plateNumber}
            </p>

            <div className="mt-5">
              <EvaluationProgress status={evaluation.status} assigned={evaluation.assigned} />
            </div>

            <div className="mt-5 grid divide-y border-t pt-4 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
              <SummaryMeta
                icon={CalendarDays}
                label="방문 희망일"
                value={formatVisitDate(evaluation.visitDate)}
                emphasis
              />
              <SummaryMeta icon={MapPin} label="방문 위치" value={evaluation.visitAddress} />
              {/* 끝낸 건은 접수 시각 대신 끝낸 시각을 보여준다. 완료 목록이 이 값의 역순으로
                  서므로, 접수 시각이 그 자리에 있으면 순서가 뒤죽박죽으로 읽힌다 */}
              {evaluation.completedAt ? (
                <SummaryMeta
                  icon={CircleCheckBig}
                  label="진단 완료"
                  value={formatDateTime(evaluation.completedAt)}
                />
              ) : (
                <SummaryMeta
                  icon={Clock3}
                  label="신청 날짜"
                  value={formatDateTime(evaluation.requestedAt)}
                />
              )}
            </div>
          </div>

          {action && (
            <div className="flex items-center border-t p-5 lg:border-t-0 lg:border-l">
              {action}
            </div>
          )}
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="h-full gap-5">
      <CardHeader className="gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="text-lg">
              {MANUFACTURER_LABEL[evaluation.manufacturer]} {evaluation.model}
            </CardTitle>
            <p className="text-muted-foreground mt-2 text-sm">
              {evaluation.modelYear}년식 · {evaluation.plateNumber}
            </p>
          </div>
          {auctionBadge}
        </div>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <EvaluationProgress status={evaluation.status} assigned={evaluation.assigned} />
        <div className="flex gap-3">
          <CalendarDays className="text-muted-foreground mt-0.5 size-4 shrink-0" />
          <div>
            <p className="font-medium">{formatVisitDate(evaluation.visitDate)}</p>
            <p className="text-muted-foreground mt-0.5 text-xs">방문 희망일</p>
          </div>
        </div>
        <div className="flex gap-3">
          <MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" />
          <p className="line-clamp-2 break-words" title={evaluation.visitAddress}>
            {evaluation.visitAddress}
          </p>
        </div>
        <p className="text-muted-foreground pt-1 text-xs">
          {formatDateTime(evaluation.requestedAt)} 접수
        </p>
      </CardContent>
      {action && <CardFooter className="mt-auto">{action}</CardFooter>}
    </Card>
  )
}

function SummaryMeta({
  icon: Icon,
  label,
  value,
  emphasis = false,
}: {
  icon: typeof CalendarDays
  label: string
  value: string
  emphasis?: boolean
}) {
  return (
    <div className="min-w-0 py-3 first:pt-0 last:pb-0 sm:px-4 sm:py-0 sm:first:pl-0 sm:last:pr-0">
      <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium">
        <Icon className="size-3.5" />
        {label}
      </p>
      <p className={`mt-2 line-clamp-2 break-words text-sm leading-5 ${emphasis ? 'font-semibold' : 'font-medium'}`} title={value}>
        {value}
      </p>
    </div>
  )
}
