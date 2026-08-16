import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, ExternalLink, LoaderCircle } from 'lucide-react'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { EmptyState } from '@/components/common/empty-state'
import { getErrorMessage } from '@/lib/axios'
import { approveDealerApplication, fetchDealerApplicationDetail, rejectDealerApplication } from '../api'
import { DEALER_APPLICATIONS_QUERY_KEY, dealerApplicationDetailQueryKey } from '../query-keys'
import {
  DEALER_APPLICATION_STATUS_LABEL,
  MAX_REJECT_REASON_LENGTH,
  type DealerApplicationDetail,
} from '../types'

export function DealerApplicationDetailPage() {
  const { applicationId: rawId } = useParams()
  const applicationId = Number(rawId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [rejectReason, setRejectReason] = useState('')

  const isInvalidId = !Number.isInteger(applicationId) || applicationId <= 0
  const detailQuery = useQuery({
    queryKey: dealerApplicationDetailQueryKey(applicationId),
    queryFn: () => fetchDealerApplicationDetail(applicationId),
    enabled: !isInvalidId,
  })

  /** 판정 뒤에는 목록 전체를 낡은 것으로 본다. 대기에서 빠지고 승인·반려로 들어가 둘 다 바뀐다 */
  const invalidateAfterDecision = () => {
    void queryClient.invalidateQueries({ queryKey: DEALER_APPLICATIONS_QUERY_KEY })
    void queryClient.invalidateQueries({
      queryKey: dealerApplicationDetailQueryKey(applicationId),
    })
  }

  const approveMutation = useMutation({
    mutationFn: () => approveDealerApplication(applicationId),
    onSuccess: () => {
      // 승인은 그 회원의 세션을 끊는다. 당사자가 다시 로그인해야 딜러로 동작한다는 것을 관리자도 알아야
      // 문의를 받았을 때 답할 수 있다
      toast.success('승인했습니다. 신청자는 다시 로그인하면 딜러로 활동할 수 있습니다')
      invalidateAfterDecision()
      navigate('/admin', { replace: true })
    },
    onError: (error) => toast.error(getErrorMessage(error, '승인하지 못했습니다')),
  })

  const rejectMutation = useMutation({
    mutationFn: () => {
      const reason = rejectReason.trim()
      if (!reason) throw new Error('반려 사유를 입력해 주세요.')
      if (reason.length > MAX_REJECT_REASON_LENGTH) {
        throw new Error(`반려 사유는 ${MAX_REJECT_REASON_LENGTH}자까지 입력할 수 있습니다.`)
      }
      return rejectDealerApplication(applicationId, reason)
    },
    onSuccess: () => {
      toast.success('반려했습니다')
      setRejectDialogOpen(false)
      setRejectReason('')
      invalidateAfterDecision()
      navigate('/admin', { replace: true })
    },
    onError: (error) => {
      // 사유 검증은 여기서 막혀 서버까지 가지 않는다. 그때는 axios 에러가 아니라 우리가 던진 것이다
      const localMessage = error instanceof Error && !('response' in error) ? error.message : null
      toast.error(localMessage ?? getErrorMessage(error, '반려하지 못했습니다'))
    },
  })

  if (isInvalidId) {
    return <DetailFallback title="잘못된 신청 번호입니다" />
  }
  if (detailQuery.isLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center">
        <LoaderCircle className="size-7 animate-spin" aria-label="상세 불러오는 중" />
      </main>
    )
  }
  if (detailQuery.isError || !detailQuery.data) {
    return (
      <DetailFallback
        title="신청 상세를 불러오지 못했습니다"
        description={getErrorMessage(detailQuery.error, '존재하지 않는 신청입니다.')}
      />
    )
  }

  const detail = detailQuery.data
  const isPending = detail.status === 'PENDING'
  const isDeciding = approveMutation.isPending || rejectMutation.isPending

  return (
    <main className="bg-muted/30 min-h-full" aria-label="딜러 심사 상세">
      <div className="mx-auto max-w-4xl px-6 py-12">
        <Button asChild variant="ghost" size="sm" className="-ml-2">
          <Link to="/admin">
            <ArrowLeft />
            운영 관리
          </Link>
        </Button>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{detail.realName}</h1>
          <Badge variant="outline">{DEALER_APPLICATION_STATUS_LABEL[detail.status]}</Badge>
        </div>

        <div className="mt-8 grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>신청자 정보</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <Field label="아이디" value={detail.username} />
              <Field label="실명" value={detail.realName} />
              <Field label="이메일" value={detail.email} />
              <Field label="휴대전화" value={detail.phone} />
              <Field label="신청 시각" value={detail.appliedAt.replace('T', ' ').slice(0, 16)} />
              {detail.rejectReason && <Field label="반려 사유" value={detail.rejectReason} />}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>자동차매매사원증</CardTitle>
            </CardHeader>
            <CardContent>
              <LicenseViewer detail={detail} onReload={() => void detailQuery.refetch()} />
            </CardContent>
          </Card>
        </div>

        {isPending ? (
          <div className="mt-8 flex flex-wrap gap-3">
            <Button size="lg" onClick={() => approveMutation.mutate()} disabled={isDeciding}>
              승인
            </Button>
            <Button
              size="lg"
              variant="outline"
              onClick={() => setRejectDialogOpen(true)}
              disabled={isDeciding}
            >
              반려
            </Button>
          </div>
        ) : (
          <p className="text-muted-foreground mt-8 text-sm">
            이미 심사가 끝난 신청입니다. 판정은 되돌릴 수 없습니다.
          </p>
        )}
      </div>

      <Dialog open={rejectDialogOpen} onOpenChange={setRejectDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>딜러 신청 반려</DialogTitle>
            <DialogDescription>
              사유는 신청자에게 그대로 전달됩니다. 무엇이 부족했는지 알 수 있게 적어 주세요.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reject-reason">반려 사유</Label>
            <textarea
              id="reject-reason"
              className="border-input placeholder:text-muted-foreground focus-visible:ring-ring min-h-28 w-full rounded-md border bg-transparent px-3 py-2 text-sm focus-visible:ring-1 focus-visible:outline-none"
              maxLength={MAX_REJECT_REASON_LENGTH}
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              placeholder="예) 사원증 사진이 흐려 확인할 수 없습니다."
            />
            <p className="text-muted-foreground text-right text-xs">
              {rejectReason.length} / {MAX_REJECT_REASON_LENGTH}
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectDialogOpen(false)}>
              취소
            </Button>
            <Button
              variant="destructive"
              onClick={() => rejectMutation.mutate()}
              disabled={rejectMutation.isPending}
            >
              반려하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}

/**
 * 사원증. <b>이미지만 가정하면 안 된다</b> — 업로드가 jpeg · png와 함께 PDF도 받으므로,
 * PDF를 img로 그리면 아무것도 보이지 않는다. 서버가 내려주는 형식으로 뷰어를 가른다.
 * <p>
 * 주소에는 만료가 있어 오래 열어 둔 화면에서는 깨지므로, 실패했을 때 다시 받을 길을 함께 둔다.
 */
function LicenseViewer({
  detail,
  onReload,
}: {
  detail: DealerApplicationDetail
  onReload: () => void
}) {
  const [failed, setFailed] = useState(false)
  const isPdf = detail.licenseContentType === 'application/pdf'

  if (failed) {
    return (
      <EmptyState
        title="사원증을 불러오지 못했습니다"
        description="조회 주소가 만료됐을 수 있습니다."
        action={
          <Button
            variant="outline"
            onClick={() => {
              setFailed(false)
              onReload()
            }}
          >
            다시 불러오기
          </Button>
        }
      />
    )
  }

  return (
    <div className="space-y-3">
      {isPdf ? (
        // 브라우저 내장 PDF 뷰어에 맡긴다. object는 렌더에 실패해도 onError를 주지 않으므로,
        // 대신 아래 새 탭 링크를 언제나 함께 둔다
        <object
          data={detail.licenseViewUrl}
          type="application/pdf"
          title={`${detail.realName}의 자동차매매사원증`}
          className="h-96 w-full rounded-lg border"
        >
          <p className="text-muted-foreground p-4 text-sm">
            이 브라우저는 PDF를 바로 보여주지 못합니다. 아래 링크로 열어 주세요.
          </p>
        </object>
      ) : (
        <img
          src={detail.licenseViewUrl}
          alt={`${detail.realName}의 자동차매매사원증`}
          className="max-h-96 w-full rounded-lg border object-contain"
          onError={() => setFailed(true)}
        />
      )}
      <a
        href={detail.licenseViewUrl}
        target="_blank"
        rel="noreferrer noopener"
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm underline-offset-4 hover:underline"
      >
        <ExternalLink className="size-4" />새 탭에서 원본 보기
      </a>
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3">
      <span className="text-muted-foreground w-20 shrink-0">{label}</span>
      <span className="min-w-0 break-words">{value}</span>
    </div>
  )
}

function DetailFallback({ title, description }: { title: string; description?: string }) {
  return (
    <main className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        title={title}
        description={description}
        action={
          <Button asChild variant="outline">
            <Link to="/admin">운영 관리로</Link>
          </Button>
        }
      />
    </main>
  )
}
