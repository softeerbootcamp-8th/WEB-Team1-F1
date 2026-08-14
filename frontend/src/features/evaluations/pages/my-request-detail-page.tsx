import { useQuery } from '@tanstack/react-query'
import {
  ArrowLeft,
  CalendarDays,
  CarFront,
  CircleCheckBig,
  FileText,
  Gavel,
  LoaderCircle,
  MapPin,
  Phone,
  UserRound,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { VehicleKeywordBadge } from '@/features/quote/components/vehicle-keyword-badge'
import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
  TRANSMISSION_LABEL,
} from '@/features/quote/types'
import { getErrorMessage } from '@/lib/axios'
import { formatDateTime, formatKRW, formatMileage } from '@/lib/format'
import { fetchEvaluationDetail } from '../api'
import { useVehicleAuctionStatus } from '../use-vehicle-auction-status'
import {
  canRegisterAuction,
  formatPhone,
  formatVisitDate,
  getAuctionBlockReason,
  getAuctionStatusMeta,
  getEvaluationStatusMeta,
} from '../utils'

export function MyRequestDetailPage() {
  const { evaluationId: evaluationIdParam } = useParams()
  const evaluationId = Number(evaluationIdParam)
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['evaluations', 'detail', evaluationId],
    queryFn: () => fetchEvaluationDetail(evaluationId),
    enabled: Number.isInteger(evaluationId) && evaluationId > 0,
  })
  // 상세 응답에는 경매 상태가 없다. 출품 버튼을 여닫을 근거는 목록에서 온다
  const { auctionStatus, isLoading: isAuctionStatusLoading } = useVehicleAuctionStatus(
    evaluationId,
    Number.isInteger(evaluationId) && evaluationId > 0,
  )

  if (!Number.isInteger(evaluationId) || evaluationId <= 0) {
    return <main className="mx-auto max-w-3xl px-6 py-24"><EmptyState title="잘못된 방문견적 번호입니다" action={<Button asChild><Link to="/mypage">마이페이지</Link></Button>} /></main>
  }

  if (query.isLoading) {
    return <main className="flex min-h-[60vh] items-center justify-center"><LoaderCircle className="size-7 animate-spin" aria-label="신청 상세 불러오는 중" /></main>
  }

  const detail = query.data
  if (query.isError || !detail) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="방문견적 신청을 찾을 수 없습니다"
          description={getErrorMessage(query.error, '존재하지 않거나 조회 권한이 없는 신청입니다.')}
          action={<Button asChild><Link to="/mypage">마이페이지</Link></Button>}
        />
      </main>
    )
  }

  const assigned = Boolean(detail.evaluatorName)
  const status = getEvaluationStatusMeta(detail.status, assigned)
  const isDiagnosed = detail.status === 'APPROVED'

  const auctionMeta = auctionStatus ? getAuctionStatusMeta(auctionStatus) : null
  // 재출품을 막는 상태만 남긴다. null 이면 출품할 수 있다는 뜻이라 아래에서 분기 하나로 쓴다
  const blockedStatus =
    auctionStatus && !canRegisterAuction(auctionStatus) ? auctionStatus : null
  const isRelisting = auctionStatus === 'FAILED'

  const postAuction = () => {
    if (!isDiagnosed || detail.estimatedPrice === null || blockedStatus) return
    navigate(`/sell/auction-post?evaluationId=${detail.evaluationId}`)
  }

  return (
    <main className="mx-auto max-w-6xl px-6 py-12" aria-label="내 방문견적 상세">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/mypage"><ArrowLeft />마이페이지</Link>
      </Button>

      <header className="mt-6 flex flex-wrap items-start justify-between gap-5">
        <div>
          <h1 className="text-3xl font-semibold md:text-4xl">
            {MANUFACTURER_LABEL[detail.manufacturer]} {detail.model}
          </h1>
          <p className="text-muted-foreground mt-2">{detail.modelYear}년식 · {detail.plateNumber}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge
            variant="outline"
            className={isDiagnosed
              ? `rounded-md px-5 py-3 text-base font-bold shadow-sm md:px-6 md:py-4 md:text-lg [&>svg]:size-5 md:[&>svg]:size-6 ${status.className}`
              : `rounded-md px-3 py-1.5 text-sm font-semibold ${status.className}`}
          >
            {isDiagnosed && <CircleCheckBig />}
            {status.label}
          </Badge>
          {auctionMeta && <Badge variant="outline" className={`rounded-full px-3 py-1 text-sm font-semibold ${auctionMeta.className}`}>{auctionMeta.label}</Badge>}
        </div>
      </header>

      <div className="mt-8 grid gap-6 lg:grid-cols-[0.78fr_1.22fr]">
        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><CalendarDays />신청 정보</CardTitle></CardHeader>
            <CardContent>
              <dl className="space-y-5 text-sm">
                <div><dt className="text-muted-foreground">방문 희망일</dt><dd className="mt-1 font-medium">{formatVisitDate(detail.visitDate)}</dd></div>
                <div><dt className="text-muted-foreground flex items-center gap-1"><MapPin className="size-3.5" />방문 주소</dt><dd className="mt-1 font-medium">{detail.visitAddress}</dd></div>
                <div><dt className="text-muted-foreground">접수 시각</dt><dd className="mt-1 font-medium">{formatDateTime(detail.requestedAt)}</dd></div>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <UserRound />{isDiagnosed ? '진단 평가사' : '평가사 배정'}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm">
              {assigned ? (
                <div className="space-y-3">
                  <p>
                    <strong>{detail.evaluatorName}</strong> 평가사가{' '}
                    {isDiagnosed ? '차량 진단을 완료했습니다.' : '방문할 예정입니다.'}
                  </p>
                  <a className="inline-flex items-center gap-2 font-medium underline-offset-4 hover:underline" href={`tel:${detail.contactPhone}`}><Phone className="size-4" />신청 연락처 {formatPhone(detail.contactPhone)}</a>
                </div>
              ) : (
                <p className="text-muted-foreground">평가사 배정을 기다리고 있습니다.</p>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><CarFront />{isDiagnosed ? '진단 결과' : '차량 정보'}</CardTitle></CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-sm">{FUEL_TYPE_LABEL[detail.fuelType]} · {TRANSMISSION_LABEL[detail.transmission]}</p>
              {detail.status === 'REJECTED' ? (
                <div className="border-destructive/20 bg-destructive/5 mt-5 rounded-xl border p-5 text-sm">
                  <p className="text-destructive font-semibold">방문 결과가 반려되었습니다</p>
                  <p className="mt-2 whitespace-pre-wrap">{detail.rejectReason}</p>
                </div>
              ) : isDiagnosed && detail.mileage !== null && detail.estimatedPrice !== null ? (
                <div className="mt-6 grid gap-4 sm:grid-cols-2">
                  <div className="bg-muted/50 rounded-xl p-5"><p className="text-muted-foreground text-sm">실측 주행거리</p><p className="mt-2 text-xl font-semibold">{formatMileage(detail.mileage)}</p></div>
                  <div className="bg-foreground text-background rounded-xl p-5"><p className="text-background/60 text-sm">평가 산정 시세</p><p className="mt-2 text-xl font-semibold">{formatKRW(detail.estimatedPrice)}</p></div>
                </div>
              ) : (
                <div className="bg-muted/50 mt-5 rounded-xl p-5 text-sm"><p className="font-medium">아직 진단 전입니다</p><p className="text-muted-foreground mt-1">평가사가 결과를 제출하면 주행거리와 산정 시세가 표시됩니다.</p></div>
              )}

              {isDiagnosed && detail.keywords.length > 0 && (
                <div className="mt-5">
                  <p className="mb-2 text-sm font-medium">차량 상태</p>
                  <div className="flex flex-wrap gap-1.5">
                    {detail.keywords.map((keyword) => (
                      <VehicleKeywordBadge key={keyword} keyword={keyword} />
                    ))}
                  </div>
                </div>
              )}

              {detail.imageUrls.length > 0 && (
                <div className="mt-6">
                  <p className="mb-3 text-sm font-medium">{isDiagnosed ? `진단 차량 사진 ${detail.imageUrls.length}장` : '차량 카탈로그 이미지'}</p>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                    {detail.imageUrls.map((url, index) => <a key={`${url}-${index}`} href={url} target="_blank" rel="noreferrer" className="relative aspect-video overflow-hidden rounded-lg bg-muted"><img src={url} alt={isDiagnosed ? `진단 차량 사진 ${index + 1}` : '차량 카탈로그 이미지'} className="size-full object-cover" />{index === 0 && isDiagnosed && <Badge className="absolute top-2 left-2">대표</Badge>}</a>)}
                  </div>
                </div>
              )}

              {isDiagnosed && detail.diagnosticReportUrl && (
                <Button asChild variant="outline" className="mt-6"><a href={detail.diagnosticReportUrl} target="_blank" rel="noreferrer"><FileText />진단서 PDF 보기</a></Button>
              )}
              {isDiagnosed && detail.submittedAt && <p className="text-muted-foreground mt-4 text-xs">{formatDateTime(detail.submittedAt)} 차량 진단 완료</p>}
            </CardContent>
          </Card>

          {/* 이미 출품된 차량은 등록 화면으로 보내 봐야 서버가 409로 돌려보낸다.
              절차를 다 밟은 뒤에 실패를 알리지 않도록 여기서 길을 닫고 이유를 적는다. */}
          {isDiagnosed && (blockedStatus ? (
            <section className="rounded-2xl border p-6 md:p-8">
              <h2 className="text-xl font-semibold">이미 경매에 등록된 차량입니다</h2>
              <p className="text-muted-foreground mt-2 text-sm">{getAuctionBlockReason(blockedStatus)}</p>
              <Button asChild variant="outline" className="mt-5"><Link to="/auctions?scope=MINE"><Gavel />나의 경매 보기</Link></Button>
            </section>
          ) : (
            <section className="bg-foreground text-background flex flex-wrap items-center justify-between gap-5 rounded-2xl p-6 md:p-8">
              <div>
                <h2 className="text-xl font-semibold">{isRelisting ? '이 차량을 다시 출품할까요?' : '이 차량을 경매에 출품할까요?'}</h2>
                <p className="text-background/60 mt-2 text-sm">{isRelisting ? '유찰된 차량은 시작가를 조정해 다시 등록할 수 있어요.' : '산정 시세를 시작가 기본값으로 이어갑니다.'}</p>
              </div>
              {/* 상태를 아직 모르는 동안은 누를 수 없다. 눌러서 등록 화면까지 갔다가
                  거기서 막히면 지금 고치려는 헛걸음이 그대로 반복된다 */}
              <Button size="lg" variant="secondary" onClick={postAuction} disabled={isAuctionStatusLoading}>
                {isAuctionStatusLoading ? <LoaderCircle className="animate-spin" /> : <Gavel />}
                {isRelisting ? '다시 등록하기' : '경매 등록하기'}
              </Button>
            </section>
          ))}
        </div>
      </div>
    </main>
  )
}
