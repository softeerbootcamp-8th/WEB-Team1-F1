import { afterEach, describe, expect, it, vi } from 'vitest'

import { axiosInstance } from '@/lib/axios'
import { prepareDocumentFile } from '@/lib/upload'

import { uploadDealDocument } from './api'

function pdf(size = 10) {
  return new File([new Uint8Array(size)], 'document.pdf', { type: 'application/pdf' })
}

describe('uploadDealDocument', () => {
  afterEach(() => vi.restoreAllMocks())

  it('거래 아래 전용 경로로 발급받아 PUT 하고 조회 주소를 돌려준다', async () => {
    const fileUrl = 'https://cdn.race.dev/documents/2026/08/doc.pdf'
    const post = vi.spyOn(axiosInstance, 'post').mockResolvedValue({
      data: {
        key: 'documents/2026/08/doc.pdf',
        uploadUrl: 'https://storage.example/upload',
        fileUrl,
        expiresAt: '2026-08-12T15:30:00',
      },
    })
    const put = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 200 }))

    // 평가사 전용인 공용 발급이 아니라, 거래가 자격을 판정할 수 있는 경로여야 한다
    await expect(uploadDealDocument(12, prepareDocumentFile(pdf()))).resolves.toBe(fileUrl)
    expect(post).toHaveBeenCalledWith('/api/deals/12/documents/presigned', {
      contentType: 'application/pdf',
      contentLength: 10,
    })

    // 서명이 묶인 주소라 공용 axios 를 태우지 않는다, baseURL·쿠키가 붙으면 S3 가 거부한다
    expect(put).toHaveBeenCalledWith(
      'https://storage.example/upload',
      expect.objectContaining({ method: 'PUT' }),
    )
  })

  it('업로드가 실패하면 조회 주소를 돌려주지 않는다', async () => {
    vi.spyOn(axiosInstance, 'post').mockResolvedValue({
      data: {
        key: 'documents/2026/08/doc.pdf',
        uploadUrl: 'https://storage.example/upload',
        fileUrl: 'https://cdn.race.dev/documents/2026/08/doc.pdf',
        expiresAt: '2026-08-12T15:30:00',
      },
    })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 403 }))

    // 올라가지 않은 파일의 주소를 거래에 등록하면 화면이 없는 서류를 가리킨다
    await expect(uploadDealDocument(12, prepareDocumentFile(pdf()))).rejects.toThrow('업로드에 실패')
  })

  it('PDF 가 아니면 발급을 요청하지도 않는다', () => {
    const post = vi.spyOn(axiosInstance, 'post')

    expect(() =>
      prepareDocumentFile(
        new File([new Uint8Array(10)], 'photo.jpg', { type: 'image/jpeg' }),
        '판매 서류',
      ),
    ).toThrow('판매 서류는 PDF')
    expect(post).not.toHaveBeenCalled()
  })
})
