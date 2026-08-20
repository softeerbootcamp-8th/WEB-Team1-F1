import { useState } from 'react'
import { toast } from 'sonner'
import { FileUp, LoaderCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { getErrorMessage } from '@/lib/axios'
import { prepareDocumentFile } from '@/lib/upload'
import {
  confirmDelivery,
  confirmPurchase,
  submitTransport,
  uploadDealDocument,
} from '../api'
import type { DealDetail } from '../types'

interface DealActionsProps {
  deal: DealDetail
  /** 전이가 끝나면 상세를 다시 읽는다. 204라 응답에 새 상태가 없다 */
  onDone: () => void
}

/**
 * 단계별 액션. <b>내 차례일 때만 렌더한다</b> — 차례가 아닌데 버튼이 보이면 눌러도 403이 오고,
 * 그건 화면이 서버와 다른 판정을 들고 있다는 뜻이다. 판정은 서버의 actionRequired 하나를 쓴다.
 *
 * 모달이 아니라 인라인인 이유. 단계마다 할 일이 하나뿐이라 띄울 것이 없고, 모달을 닫으면
 * 무엇을 해야 했는지 다시 찾아야 한다. 서류 업로드는 진행 상태가 보여야 하는데 그것도 가려진다.
 */
export function DealActions({ deal, onDone }: DealActionsProps) {
  if (!deal.actionRequired) return null

  switch (deal.status) {
    case 'BUYER_CONFIRM_PENDING':
      return <ConfirmPurchase deal={deal} onDone={onDone} />
    case 'SELLER_SUBMIT_PENDING':
      return <SubmitTransport deal={deal} onDone={onDone} />
    case 'BUYER_SCHEDULE_PENDING':
      return <ConfirmDelivery deal={deal} onDone={onDone} />
    default:
      return null
  }
}

function ConfirmPurchase({ deal, onDone }: DealActionsProps) {
  const [isPending, setIsPending] = useState(false)

  const submit = async () => {
    setIsPending(true)
    try {
      await confirmPurchase(deal.dealId)
      toast.success('구매를 확정했습니다. 판매자에게 명의이전 서류와 탁송 일정을 요청했습니다.')
      onDone()
    } catch (cause) {
      toast.error(getErrorMessage(cause, '구매 확정에 실패했습니다.'))
    } finally {
      setIsPending(false)
    }
  }

  return (
    <ActionBox title="확정 전 확인해 주세요">
      <p className="text-muted-foreground text-sm">
        확정하면 판매자가 명의이전 서류와 탁송 일정을 등록합니다. 확정 전까지는 취소할 수 있습니다.
      </p>
      <Button onClick={submit} disabled={isPending} className="mt-4">
        {isPending && <LoaderCircle className="animate-spin" />}
        구매 확정
      </Button>
    </ActionBox>
  )
}

function SubmitTransport({ deal, onDone }: DealActionsProps) {
  const [file, setFile] = useState<File | null>(null)
  const [transportAt, setTransportAt] = useState('')
  const [transportLocation, setTransportLocation] = useState('')
  const [isPending, setIsPending] = useState(false)

  const submit = async () => {
    if (!file) {
      toast.error('명의이전 서류를 첨부해 주세요.')
      return
    }

    setIsPending(true)
    try {
      // 파일은 서버를 거치지 않는다. 발급받은 주소로 브라우저가 S3 에 직접 올리고,
      // 거래에는 조회 주소만 넘긴다
      const prepared = prepareDocumentFile(file, '명의이전 서류')
      const documentUrl = await uploadDealDocument(deal.dealId, prepared)

      await submitTransport(deal.dealId, {
        documentUrl,
        // datetime-local 은 초가 없다. 서버는 LocalDateTime 이라 초까지 받아야 파싱된다
        transportAt: `${transportAt}:00`,
        transportLocation,
      })
      toast.success('명의이전 서류와 탁송 일정을 등록했습니다.')
      onDone()
    } catch (cause) {
      const fallback =
        cause instanceof Error ? cause.message : '서류와 일정 등록에 실패했습니다.'
      toast.error(getErrorMessage(cause, fallback))
    } finally {
      setIsPending(false)
    }
  }

  return (
    <ActionBox title="명의이전 서류와 탁송 정보">
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="document">명의이전 서류 (PDF, 20MB 이하)</Label>
          <Input
            id="document"
            type="file"
            accept="application/pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
          {file && (
            <p className="text-muted-foreground flex items-center gap-1 text-xs">
              <FileUp className="size-3.5" />
              {file.name}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="transportAt">탁송 출발 일시</Label>
          <Input
            id="transportAt"
            type="datetime-local"
            value={transportAt}
            onChange={(event) => setTransportAt(event.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="transportLocation">탁송 출발지</Label>
          <Input
            id="transportLocation"
            placeholder="서울시 강남구 테헤란로 123"
            value={transportLocation}
            onChange={(event) => setTransportLocation(event.target.value)}
          />
        </div>

        <Button
          onClick={submit}
          disabled={isPending || !file || !transportAt || !transportLocation.trim()}
        >
          {isPending && <LoaderCircle className="animate-spin" />}
          등록하기
        </Button>
      </div>
    </ActionBox>
  )
}

function ConfirmDelivery({ deal, onDone }: DealActionsProps) {
  const [deliveryAt, setDeliveryAt] = useState('')
  const [deliveryLocation, setDeliveryLocation] = useState('')
  const [isPending, setIsPending] = useState(false)

  const submit = async () => {
    setIsPending(true)
    try {
      await confirmDelivery(deal.dealId, {
        deliveryAt: `${deliveryAt}:00`,
        deliveryLocation,
      })
      toast.success('거래가 확정되었습니다.')
      onDone()
    } catch (cause) {
      toast.error(getErrorMessage(cause, '인수 일정 확정에 실패했습니다.'))
    } finally {
      setIsPending(false)
    }
  }

  // 탁송보다 앞선 시각은 서버가 400 으로 막지만, 고를 수 없게 두면 그 실패를 만나지 않는다.
  // 판정은 여전히 서버가 한다 — 이건 안내이지 검증이 아니다
  const earliest = deal.transportAt?.slice(0, 16)

  return (
    <ActionBox title="차량을 받을 날짜와 장소">
      <p className="text-muted-foreground text-sm">
        판매자가 등록한 탁송 출발 이후로 정해야 합니다. 등록하면 거래가 확정됩니다.
      </p>

      <div className="mt-4 space-y-4">
        <div className="space-y-2">
          <Label htmlFor="deliveryAt">차량 인수 일시</Label>
          <Input
            id="deliveryAt"
            type="datetime-local"
            min={earliest}
            value={deliveryAt}
            onChange={(event) => setDeliveryAt(event.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="deliveryLocation">차량 인수 장소</Label>
          <Input
            id="deliveryLocation"
            placeholder="부산시 해운대구 센텀중앙로 55"
            value={deliveryLocation}
            onChange={(event) => setDeliveryLocation(event.target.value)}
          />
        </div>

        <Button onClick={submit} disabled={isPending || !deliveryAt || !deliveryLocation.trim()}>
          {isPending && <LoaderCircle className="animate-spin" />}
          거래 확정하기
        </Button>
      </div>
    </ActionBox>
  )
}

function ActionBox({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="border-deal-active/20 bg-deal-active/5 mt-8 rounded-xl border p-6" aria-label="할 일">
      <h2 className="text-lg font-semibold">{title}</h2>
      <div className="mt-3">{children}</div>
    </section>
  )
}
