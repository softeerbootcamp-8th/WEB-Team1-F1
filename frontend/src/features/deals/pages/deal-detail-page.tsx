import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  ArrowLeft,
  CalendarClock,
  FileText,
  Gavel,
  LoaderCircle,
  MapPin,
  UserRound,
} from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { CarThumb } from '@/components/common/car-thumb'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { getErrorMessage } from '@/lib/axios'
import { cn } from '@/lib/utils'
import { formatDateTime, formatKRW, formatMileage } from '@/lib/format'
import { cancelDeal, fetchDealDetail } from '../api'
import { DealActions } from '../components/deal-actions'
import { DEAL_FLOW, DEAL_STATUS_META, dealGuide } from '../types'
import type { DealStatus } from '../types'

/**
 * 거래 상세. 낙찰 알림이 가리키는 목적지이고, 단계를 실제로 진행시키는 화면이다.
 *
 * 액션은 전부 204 라 응답에 새 상태가 없다. 그래서 전이 뒤에는 상세를 다시 읽어 화면을 맞춘다 —
 * 화면이 다음 단계를 스스로 계산하면 서버의 단계 표를 복제하게 되고, 둘이 어긋나는 순간 조용히 틀린다.
 */
export function DealDetailPage() {
  const { dealId: dealIdParam } = useParams()
  const dealId = Number(dealIdParam)
  const queryClient = useQueryClient()
  const [isCancelling, setIsCancelling] = useState(false)

  const query = useQuery({
    queryKey: ['deals', 'detail', dealId],
    queryFn: () => fetchDealDetail(dealId),
    enabled: Number.isInteger(dealId) && dealId > 0,
  })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['deals', 'detail', dealId] })
  }

  const cancel = async () => {
    // 되돌릴 수 없는 행동이라 한 번 묻는다. 확정 전이라도 상대는 이미 준비를 시작했을 수 있다
    if (!window.confirm('거래를 그만두시겠습니까? 취소한 쪽이 귀책으로 남습니다.')) return

    setIsCancelling(true)
    try {
      await cancelDeal(dealId)
      toast.success('거래를 취소했습니다.')
      refresh()
    } catch (cause) {
      toast.error(getErrorMessage(cause, '거래 취소에 실패했습니다.'))
    } finally {
      setIsCancelling(false)
    }
  }

  if (!Number.isInteger(dealId) || dealId <= 0) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="거래 상세">
        <EmptyState
          title="잘못된 거래 번호입니다"
          action={
            <Button asChild>
              <Link to="/deals">내 거래</Link>
            </Button>
          }
        />
      </main>
    )
  }

  if (query.isLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center">
        <LoaderCircle className="size-7 animate-spin" aria-label="거래 상세 불러오는 중" />
      </main>
    )
  }

  const deal = query.data
  if (query.isError || !deal) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="거래 상세">
        {/* 없는 거래와 남의 거래를 서버가 구분해 주지 않으므로 화면도 한 문장으로 답한다 */}
        <EmptyState
          title="거래를 찾을 수 없습니다"
          description={getErrorMessage(query.error, '존재하지 않거나 조회 권한이 없는 거래입니다.')}
          action={
            <Button asChild>
              <Link to="/deals">내 거래</Link>
            </Button>
          }
        />
      </main>
    )
  }

  const cancelled = deal.status === 'CANCELLED'
  const meta = DEAL_STATUS_META[deal.status]

  return (
    <main className="mx-auto max-w-5xl px-6 py-12" aria-label="거래 상세">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/deals">
          <ArrowLeft />
          내 거래
        </Link>
      </Button>

      <header className="mt-6 flex flex-wrap items-start justify-between gap-5">
        <div className="flex min-w-0 gap-5">
          <div className="bg-muted size-24 shrink-0 overflow-hidden rounded-lg border">
            <CarThumb src={deal.thumbnailUrl ?? undefined} alt={deal.model} loading="eager" />
          </div>
          <div className="min-w-0">
            <p className="text-muted-foreground text-sm">거래 #{deal.dealId}</p>
            <h1 className="mt-1 truncate text-3xl font-semibold md:text-4xl">{deal.model}</h1>
            <p className="text-muted-foreground mt-2">
              {deal.modelYear}년식 · {formatMileage(deal.mileage)}
            </p>
          </div>
        </div>

        <div className="text-right">
          <Badge variant={cancelled ? 'destructive' : deal.status === 'CONFIRMED' ? 'success' : 'secondary'}>
            {meta.label}
          </Badge>
          <p className="tabular mt-3 text-2xl font-semibold">{formatKRW(deal.finalPrice)}</p>
          <p className="text-muted-foreground text-xs">낙찰가</p>
        </div>
      </header>

      {!cancelled && <DealSteps status={deal.status} />}

      {/* 내 차례인지 아닌지에 따라 문장이 갈린다, "나는 기다리면 되는가"가 한눈에 읽혀야 한다 */}
      <p
        className={cn(
          'mt-6 rounded-lg border px-5 py-4 text-sm',
          deal.actionRequired ? 'border-primary/40 bg-muted font-medium' : 'text-muted-foreground',
        )}
      >
        {dealGuide(deal.status, deal.actionRequired)}
      </p>

      <DealActions deal={deal} onDone={refresh} />

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <UserRound />
              거래 정보
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-5 text-sm">
              <Row label={deal.mySide === 'BUYER' ? '판매자' : '구매자'} value={deal.counterpartName} />
              <Row label="낙찰 시각" value={formatDateTime(deal.openedAt)} />
              <Row label="현재 단계로 넘어온 시각" value={formatDateTime(deal.statusChangedAt)} />
              {cancelled && (
                <Row
                  label="취소"
                  value={`${deal.faultParty === 'BUYER' ? '구매자' : '판매자'}가 거래를 그만두었습니다.`}
                />
              )}
            </dl>
            <Button asChild variant="outline" size="sm" className="mt-6">
              <Link to={`/auctions/${deal.auctionId}`}>
                <Gavel />
                경매 결과 보기
              </Link>
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CalendarClock />
              인수 일정
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-5 text-sm">
              <Row
                label="출차"
                value={
                  deal.transportAt
                    ? `${formatDateTime(deal.transportAt)} · ${deal.transportLocation}`
                    : null
                }
                fallback="판매자가 아직 등록하지 않았습니다."
              />
              <Row
                label="인도"
                value={
                  deal.deliveryAt
                    ? `${formatDateTime(deal.deliveryAt)} · ${deal.deliveryLocation}`
                    : null
                }
                fallback="구매자가 아직 정하지 않았습니다."
              />
              <div>
                <dt className="text-muted-foreground flex items-center gap-1">
                  <FileText className="size-3.5" />
                  판매 서류
                </dt>
                <dd className="mt-1">
                  {deal.documentUrl ? (
                    <a
                      className="font-medium underline-offset-4 hover:underline"
                      href={deal.documentUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      서류 열기
                    </a>
                  ) : (
                    <span className="text-muted-foreground">판매자가 아직 제출하지 않았습니다.</span>
                  )}
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>
      </div>

      {/* 확정 전까지는 양쪽 누구든 그만둘 수 있다. 내 차례가 아니어도 빠질 수 있어야 한다 */}
      {!cancelled && deal.status !== 'CONFIRMED' && (
        <div className="mt-8 border-t pt-6">
          <Button variant="ghost" size="sm" onClick={cancel} disabled={isCancelling}>
            거래 그만두기
          </Button>
        </div>
      )}

      {deal.status === 'CONFIRMED' && (
        <p className="text-muted-foreground mt-8 flex items-start gap-2 text-sm">
          <MapPin className="mt-0.5 size-4 shrink-0" />
          {/* 이전 등록은 자동차365가 이미 제공하고, 끝났는지 확인할 수단이 우리에게 없다 */}
          명의이전은 자동차365에서 직접 진행합니다. 대금은 만나서 당사자끼리 주고받습니다.
        </p>
      )}
    </main>
  )
}

/** 진행 단계. 서버가 내려준 단계 하나로 어디까지 왔는지 표시한다 */
function DealSteps({ status }: { status: DealStatus }) {
  const current = DEAL_FLOW.indexOf(status)

  return (
    <ol className="mt-8 flex gap-2" aria-label="거래 진행 단계">
      {DEAL_FLOW.map((step, index) => (
        <li key={step} className="flex-1">
          <div
            className={cn(
              'h-1 rounded-full',
              index <= current ? 'bg-deal-done' : 'bg-muted',
            )}
          />
          <p
            className={cn(
              'mt-2 text-xs',
              index === current ? 'text-foreground font-medium' : 'text-muted-foreground',
            )}
          >
            {DEAL_STATUS_META[step].step}
          </p>
        </li>
      ))}
    </ol>
  )
}

function Row({
  label,
  value,
  fallback,
}: {
  label: string
  value: string | null
  fallback?: string
}) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={cn('mt-1', value ? 'font-medium' : 'text-muted-foreground')}>
        {value ?? fallback}
      </dd>
    </div>
  )
}
