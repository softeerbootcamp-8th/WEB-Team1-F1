import { axiosInstance } from '@/lib/axios'
import { putPreparedFile, type PreparedUploadFile } from '@/lib/upload'
import type {
  DealDetail,
  DealDocumentUpload,
  DealSlice,
  DeliveryConfirmRequest,
  TransportSubmitRequest,
} from './types'

/** 내가 당사자인 거래 한 페이지. 커서가 없으면 첫 페이지 */
export async function fetchDealList(cursor?: number): Promise<DealSlice> {
  const { data } = await axiosInstance.get<DealSlice>('/api/deals', {
    params: cursor == null ? undefined : { cursor },
  })
  return data
}

/**
 * 없는 거래와 남의 거래가 모두 404 로 온다. 서버가 둘을 구분해 주지 않으므로 화면도 구분하지 않는다 —
 * 구분하면 그 번호의 거래가 존재한다는 사실이 새어 나간다.
 */
export async function fetchDealDetail(dealId: number): Promise<DealDetail> {
  const { data } = await axiosInstance.get<DealDetail>(`/api/deals/${dealId}`)
  return data
}

/*
 * 아래 넷은 모두 204 다. 응답 본문이 없으므로 호출한 쪽이 상세를 다시 읽어 화면을 맞춘다 —
 * 서버가 전이 후 상태를 내려주게 하면 목록·상세가 각자 다른 시점의 값을 들게 된다.
 *
 * 상대 차례에 보내면 403, 이미 지난 단계면 409, 동시에 두 번 눌러 겹치면 409(낙관적 락)다.
 */

/** 구매자가 구매를 확정한다 */
export async function confirmPurchase(dealId: number): Promise<void> {
  await axiosInstance.post(`/api/deals/${dealId}/confirmation`)
}

/**
 * 명의이전 서류 한 건을 올리고 탁송 제출에 쓸 조회 주소를 받는다.
 *
 * 공용 발급(`/api/uploads/presigned`)이 아니라 거래 아래의 경로다. 공용 쪽은 차량 평가 경로라
 * 평가사만 부를 수 있고, 서류를 낼 자격은 역할이 아니라 "이 거래에서 지금 움직일 판매자인가"라
 * 거래가 판정한다. 상대 차례면 403, 끝난 거래면 409 다.
 */
export async function uploadDealDocument(
  dealId: number,
  prepared: PreparedUploadFile,
): Promise<string> {
  const { data } = await axiosInstance.post<DealDocumentUpload>(
    `/api/deals/${dealId}/documents/presigned`,
    { contentType: prepared.contentType, contentLength: prepared.file.size },
  )

  await putPreparedFile(prepared, data.uploadUrl)

  return data.fileUrl
}

/** 판매자가 명의이전 서류와 탁송 일정을 낸다 */
export async function submitTransport(
  dealId: number,
  request: TransportSubmitRequest,
): Promise<void> {
  await axiosInstance.post(`/api/deals/${dealId}/transport`, request)
}

/** 구매자가 인수 일정을 잡아 거래가 확정된다 */
export async function confirmDelivery(
  dealId: number,
  request: DeliveryConfirmRequest,
): Promise<void> {
  await axiosInstance.post(`/api/deals/${dealId}/delivery`, request)
}

/** 확정 전까지 양쪽 누구든. 그만둔 쪽이 귀책으로 남는다 */
export async function cancelDeal(dealId: number): Promise<void> {
  await axiosInstance.post(`/api/deals/${dealId}/cancellation`)
}
