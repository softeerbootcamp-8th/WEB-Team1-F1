import { CalendarDays, MapPin } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { formatDateTime } from '@/lib/format'
import type { EvaluationSummary } from '../types'
import { formatVisitDate, getAuctionStatusMeta, getEvaluationStatusMeta } from '../utils'

interface EvaluationSummaryCardProps {
  evaluation: EvaluationSummary
  action?: React.ReactNode
  layout?: 'card' | 'list'
}

export function EvaluationSummaryCard({
  evaluation,
  action,
  layout = 'card',
}: EvaluationSummaryCardProps) {
  const status = getEvaluationStatusMeta(evaluation.status, evaluation.assigned)
  const auctionStatus = evaluation.auctionStatus
    ? getAuctionStatusMeta(evaluation.auctionStatus)
    : null

  const statusBadges = (
    <div className="flex flex-wrap gap-2">
      <Badge variant="outline" className={status.className}>
        {status.label}
      </Badge>
      {auctionStatus && (
        <Badge variant="outline" className={auctionStatus.className}>
          {auctionStatus.label}
        </Badge>
      )}
    </div>
  )

  if (layout === 'list') {
    return (
      <Card className="gap-0 py-0">
        <CardContent className="flex flex-col gap-5 p-5 sm:p-6 lg:flex-row lg:items-center">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-lg font-semibold">
                {MANUFACTURER_LABEL[evaluation.manufacturer]} {evaluation.model}
              </h2>
              {statusBadges}
            </div>

            <p className="text-muted-foreground mt-2 text-sm">
              {evaluation.modelYear}년식 · {evaluation.plateNumber}
            </p>

            <div className="mt-4 grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-[1fr_1.4fr_auto]">
              <div className="flex min-w-0 gap-2.5">
                <CalendarDays className="text-muted-foreground mt-0.5 size-4 shrink-0" />
                <strong>{formatVisitDate(evaluation.visitDate)}</strong>
              </div>
              <div className="flex min-w-0 gap-2.5">
                <MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" />
                <span className="break-words">{evaluation.visitAddress}</span>
              </div>
              <p className="text-muted-foreground text-xs md:col-span-2 xl:col-span-1 xl:self-center xl:text-right">
                {formatDateTime(evaluation.requestedAt)} 접수
              </p>
            </div>
          </div>

          {action && (
            <div className="shrink-0 lg:w-48 lg:border-l lg:pl-6">
              {action}
            </div>
          )}
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="h-full gap-5">
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="text-lg">
              {MANUFACTURER_LABEL[evaluation.manufacturer]} {evaluation.model}
            </CardTitle>
            <p className="text-muted-foreground mt-2 text-sm">
              {evaluation.modelYear}년식 · {evaluation.plateNumber}
            </p>
          </div>
          {statusBadges}
        </div>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <div className="flex gap-3">
          <CalendarDays className="text-muted-foreground mt-0.5 size-4 shrink-0" />
          <div>
            <p className="font-medium">{formatVisitDate(evaluation.visitDate)}</p>
            <p className="text-muted-foreground mt-0.5 text-xs">방문 희망일</p>
          </div>
        </div>
        <div className="flex gap-3">
          <MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" />
          <p className="break-words">{evaluation.visitAddress}</p>
        </div>
        <p className="text-muted-foreground pt-1 text-xs">
          {formatDateTime(evaluation.requestedAt)} 접수
        </p>
      </CardContent>
      {action && <CardFooter className="mt-auto">{action}</CardFooter>}
    </Card>
  )
}
