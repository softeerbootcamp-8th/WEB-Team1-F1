import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ChartLine,
  FileText,
  Flag,
  ListFilter,
  Lock,
  SquareArrowOutUpRight,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Countdown } from '@/components/common/countdown'
import { StartAlertButton } from '@/features/auctions/components/start-alert-button'
import { useAuth } from '@/features/auth/auth-context'
import { CarPhotos } from '@/features/auction-room/components/car-detail'
import { fetchAuctionRoom, fetchRoomOpening, fetchRoomResult } from '@/features/auction-room/api'
import type { RoomOpeningView, RoomResultView } from '@/features/auction-room/types'
import { similarFilter, type AuctionVehicleFilter } from '@/features/auctions/filter'
import type { AuctionListCard } from '@/features/auctions/types'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL } from '@/features/quote/types'
import { getErrorCode, getErrorMessage } from '@/lib/axios'
import { formatClock, formatKRW, formatMileage } from '@/lib/format'

/** 목록 위 미리보기로 열 수 있는 단계 */
export type PreviewStatus = 'NOT_OPEN' | 'ENDED'

interface AuctionPreviewDialogProps {
  auctionId: number | null
  /** 열 때 판정한 단계, 열려 있는 동안 시각이 지나도 화면을 갈아엎지 않게 고정한다 */
  status: PreviewStatus
  /**
   * 목록에 실려 있는 카드. 마감 시각처럼 상세 응답에 없는 값을 채운다.
   * 알림으로 바로 들어와 목록에 아직 없는 경매는 카드 없이 열리므로 없을 수 있다.
   */
  card?: AuctionListCard | null
  /** 서버 시각 - 브라우저 시계 */
  offsetMs: number
  onOpenChange: (open: boolean) => void
  /** 비슷한 조건으로 목록을 갈아끼운다. 이 화면은 목록을 떠나지 않는 것이 목적이라 이동이 아니다 */
  onSimilar: (filter: AuctionVehicleFilter) => void
}

/**
 * 목록 위에서 여는 경매 미리보기. 입장할 수 없는 두 단계(입장 전·끝난 경매)를 담는다.
 *
 * 목록을 떠나지 않는 것이 이 화면의 목적이다. 두 단계 모두 입찰할 수 없어 방에 들어갈 이유가
 * 없고, 사용자가 하는 일은 여러 경매를 훑어 비교하는 것이라 페이지를 옮기면 그 흐름이 끊긴다.
 *
 * 사진 여러 장과 진단서는 목록 카드에 없어 열릴 때 받아온다. 방 조회와 같은 두 엔드포인트를
 * 그대로 쓰므로 백엔드에 새로 여는 것이 없다.
 */
export function AuctionPreviewDialog({
  auctionId,
  status,
  card,
  offsetMs,
  onOpenChange,
  onSimilar,
}: AuctionPreviewDialogProps) {
  const navigate = useNavigate()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [detail, setDetail] = useState<RoomOpeningView | RoomResultView | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** 실제로 받아온 것이 무엇인지. 넘겨받은 단계가 낡았으면 여기서 갈린다 */
  const [loaded, setLoaded] = useState<PreviewStatus>(status)

  useEffect(() => {
    if (auctionId === null) return

    let alive = true
    setDetail(null)
    setError(null)
    setLoaded(status)

    // 상세 응답은 로그인한 회원에게만 내려온다. 비로그인 요청을 먼저 보내면 401이
    // 일반적인 조회 실패처럼 보여, 왜 못 보는지와 다음 행동이 모두 사라진다.
    if (isAuthLoading || !isAuthenticated) {
      return () => {
        alive = false
      }
    }

    const settle = (data: RoomOpeningView | RoomResultView, as: PreviewStatus) => {
      if (!alive) return
      setDetail(data)
      setLoaded(as)
    }

    if (status === 'ENDED') {
      fetchRoomResult(auctionId)
        // 마감과 확정 사이에는 결과가 없다, 서버가 거절하는 짧은 구간이라 사유를 그대로 보여준다
        .then((d) => settle(d, 'ENDED'))
        .catch((e) => alive && setError(getErrorMessage(e, '정보를 불러오지 못했습니다')))
      return () => {
        alive = false
      }
    }

    // 단계를 모른 채 들어온 딥링크는 입장 전으로 본다. opening이 거절하는 사유는 늘 같은 코드다 —
    // RoomPhase.openingRejection()이 NOT_OPEN이 아닌 단계를 전부 ROOM_ALREADY_OPEN 하나로
    // 뭉뚱그리므로, 그새 열려서 지금 들어갈 수 있는 것인지 완전히 끝난 것인지 이 응답만으로는
    // 못 가른다. 방 조회로 실제 상태를 물어 정확히 가른다
    fetchRoomOpening(auctionId)
      .then((d) => settle(d, 'NOT_OPEN'))
      .catch(() =>
        fetchAuctionRoom(auctionId)
          .then(() => {
            // 이미 들어갈 수 있는 방이다, 미리보기가 아니라 진짜 방으로 보낸다
            if (alive) navigate(`/auctions/${auctionId}`, { replace: true })
          })
          .catch((e: unknown) => {
            if (!alive) return
            if (getErrorCode(e) !== 'ROOM_ALREADY_CLOSED') {
              setError(getErrorMessage(e, '정보를 불러오지 못했습니다'))
              return
            }
            fetchRoomResult(auctionId)
              .then((d) => settle(d, 'ENDED'))
              .catch((e2) => alive && setError(getErrorMessage(e2, '정보를 불러오지 못했습니다')))
          }),
      )

    return () => {
      alive = false
    }
  }, [auctionId, status, navigate, isAuthenticated, isAuthLoading])

  const result = loaded === 'ENDED' ? (detail as RoomResultView | null) : null
  const vehicle = detail?.vehicle
  const model = vehicle?.model ?? card?.model
  const startPrice = detail?.startPrice ?? card?.startPrice
  const openAt = (detail as RoomOpeningView | null)?.openAt ?? card?.openAt
  const startAt = (detail as RoomOpeningView | null)?.startAt ?? card?.startAt

  // 헤더가 쓰는 것과 같은 값을 기준으로 잡는다. 유찰이면 낙찰가가 없어 시작가로 내려간다
  const manufacturer = vehicle?.manufacturer ?? card?.manufacturer
  const similar =
    manufacturer !== undefined && startPrice !== undefined
      ? similarFilter(manufacturer, result?.winningPrice ?? startPrice)
      : null

  return (
    <Dialog open={auctionId !== null} onOpenChange={onOpenChange}>
      <DialogContent className="gap-0 overflow-hidden p-0 sm:max-w-lg">
        <DialogHeader className="flex-row items-start justify-between gap-4 space-y-0 border-b p-5 pr-12 text-left">
          <div>
            <DialogTitle className="text-xl">
              {vehicle && `${MANUFACTURER_LABEL[vehicle.manufacturer]} `}
              {model ?? '경매'}
            </DialogTitle>
            <DialogDescription className="mt-1 text-base">
              {vehicle
                ? `${vehicle.modelYear}년 · ${formatMileage(vehicle.mileage)} · ${FUEL_TYPE_LABEL[vehicle.fuelType]}`
                : '불러오는 중'}
            </DialogDescription>
          </div>

          <div className="shrink-0 text-right">
            {loaded === 'NOT_OPEN' ? (
              <>
                <p className="text-muted-foreground flex items-center justify-end gap-1.5 text-base">
                  <Lock className="size-4" />
                  입장까지
                </p>
                {openAt && (
                  <Countdown
                    targetIso={openAt}
                    offsetMs={offsetMs}
                    className="mt-0.5 block text-3xl font-bold"
                  />
                )}
              </>
            ) : (
              <>
                <p className="text-muted-foreground flex items-center justify-end gap-1.5 text-base">
                  <Flag className="size-4" />
                  {result?.outcome === 'UNSOLD' ? '유찰' : '최종 낙찰가'}
                </p>
                <p className="text-price-up tabular mt-0.5 text-3xl font-bold">
                  {result?.winningPrice == null ? '—' : formatKRW(result.winningPrice)}
                </p>
              </>
            )}
          </div>
        </DialogHeader>

        {isAuthLoading ? (
          <p className="text-muted-foreground p-10 text-center text-base">로그인 상태를 확인하고 있습니다</p>
        ) : !isAuthenticated ? (
          <p className="text-muted-foreground p-10 text-center text-base">로그인이 필요합니다.</p>
        ) : error ? (
          <p className="text-muted-foreground p-10 text-center text-base">{error}</p>
        ) : (
          <>
            {/* 받아오기 전에는 목록이 가진 대표 사진 한 장으로 자리를 채운다 */}
            <CarPhotos
              model={model ?? ''}
              imageUrls={vehicle?.imageUrls ?? (card?.thumbnailUrl ? [card.thumbnailUrl] : [''])}
              aspectClassName="aspect-[16/9]"
              className="p-4"
            />

            <dl className="text-muted-foreground flex flex-wrap gap-x-6 gap-y-2 border-t p-4 text-base">
              {loaded === 'NOT_OPEN' ? (
                <>
                  {openAt && <Fact label="입장" value={formatClock(openAt)} />}
                  {startAt && <Fact label="입찰 시작" value={formatClock(startAt)} />}
                  {card && <Fact label="마감" value={formatClock(card.endAt)} />}
                </>
              ) : (
                <>
                  {startPrice !== undefined && (
                    <Fact label="시작가" value={formatKRW(startPrice)} />
                  )}
                  <Fact label="입찰" value={`${result?.bidCount ?? 0}건`} />
                  <Fact label="상승률" value={riseRate(result)} />
                </>
              )}
            </dl>

            {vehicle && (
              <a
                href={vehicle.diagnosticReportUrl}
                target="_blank"
                rel="noreferrer"
                className="hover:bg-muted/60 flex items-center justify-between border-t p-4 text-base"
              >
                <span className="flex items-center gap-2">
                  <FileText className="size-4" />
                  진단서 보기
                </span>
                <SquareArrowOutUpRight className="text-muted-foreground size-4" />
              </a>
            )}

            {/* 오른쪽은 액션 자리다. 입장 전에는 개장 알림 받기가 여기 들어온다 */}
            <div className="bg-muted/40 flex min-h-18 items-center justify-between gap-4 border-t p-4">
              <div>
                <p className="text-muted-foreground text-sm">
                  {loaded === 'NOT_OPEN' ? '시작가' : '마감'}
                </p>
                <p className="tabular mt-0.5 text-xl font-bold">
                  {loaded === 'NOT_OPEN'
                    ? startPrice === undefined
                      ? '—'
                      : formatKRW(startPrice)
                    : card
                      ? formatClock(card.endAt)
                      : '—'}
                </p>
              </div>

              {loaded === 'NOT_OPEN' ? (
                auctionId !== null && <StartAlertButton auctionId={auctionId} />
              ) : (
                <div className="flex flex-wrap justify-end gap-2">
                  <Button
                    variant="outline"
                    disabled={similar === null}
                    onClick={() => similar && onSimilar(similar)}
                  >
                    <ListFilter />
                    비슷한 경매 보기
                  </Button>
                  <Button onClick={() => navigate(`/auctions/${auctionId}/result`)}>
                    <ChartLine />
                    결과 자세히 보기
                  </Button>
                </div>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-2">
      <dt>{label}</dt>
      <dd className="text-foreground tabular font-semibold">{value}</dd>
    </div>
  )
}

/** 시작가 대비 낙찰가 상승률. 유찰이면 오른 값이 없다 */
function riseRate(result: RoomResultView | null): string {
  if (result === null || result.winningPrice === null) return '—'
  const rate = ((result.winningPrice - result.startPrice) / result.startPrice) * 100
  return `+${rate.toFixed(1)}%`
}
