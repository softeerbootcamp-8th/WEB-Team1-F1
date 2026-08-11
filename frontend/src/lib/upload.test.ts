import { afterEach, describe, expect, it, vi } from 'vitest'

import { axiosInstance } from './axios'
import { MAX_DEALER_LICENSE_SIZE, prepareDealerLicenseFile } from './upload'
import { uploadDealerLicense } from './upload'

function file(name: string, type: string, size: number) {
  return new File([new Uint8Array(size)], name, { type })
}

describe('prepareDealerLicenseFile', () => {
  it.each([
    ['license.jpg', 'image/jpeg'],
    ['license.png', 'image/png'],
    ['license.pdf', 'application/pdf'],
  ])('%s 형식을 허용한다', (name, type) => {
    expect(prepareDealerLicenseFile(file(name, type, 10)).contentType).toBe(type)
  })

  it('WEBP와 알 수 없는 형식은 거부한다', () => {
    expect(() => prepareDealerLicenseFile(file('license.webp', 'image/webp', 10))).toThrow(
      'JPG, PNG, PDF',
    )
  })

  it('빈 파일과 10MB 초과 파일은 거부한다', () => {
    expect(() => prepareDealerLicenseFile(file('license.pdf', 'application/pdf', 0))).toThrow(
      '비어 있는',
    )
    expect(() =>
      prepareDealerLicenseFile(
        file('license.pdf', 'application/pdf', MAX_DEALER_LICENSE_SIZE + 1),
      ),
    ).toThrow('10MB')
  })
})

describe('uploadDealerLicense', () => {
  afterEach(() => vi.restoreAllMocks())

  it('전용 주소를 발급받아 PUT하고 회원가입에 쓸 비공개 키를 반환한다', async () => {
    const license = file('license.pdf', 'application/pdf', 10)
    const post = vi.spyOn(axiosInstance, 'post').mockResolvedValue({
      data: {
        key: 'dealer-licenses/2026/08/license.pdf',
        uploadUrl: 'https://storage.example/upload',
        expiresAt: '2026-08-11T18:00:00',
      },
    })
    const put = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 200 }))

    await expect(uploadDealerLicense(prepareDealerLicenseFile(license))).resolves.toBe(
      'dealer-licenses/2026/08/license.pdf',
    )
    expect(post).toHaveBeenCalledWith('/api/uploads/dealer-license/presigned', {
      contentType: 'application/pdf',
      contentLength: 10,
    })
    expect(put).toHaveBeenCalledWith('https://storage.example/upload', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/pdf' },
      body: license,
    })
  })

  it('저장소 PUT 실패를 사용자 메시지로 변환한다', async () => {
    vi.spyOn(axiosInstance, 'post').mockResolvedValue({
      data: {
        key: 'dealer-licenses/2026/08/license.jpg',
        uploadUrl: 'https://storage.example/upload',
        expiresAt: '2026-08-11T18:00:00',
      },
    })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 403 }))

    await expect(
      uploadDealerLicense(prepareDealerLicenseFile(file('license.jpg', 'image/jpeg', 10))),
    ).rejects.toThrow('업로드에 실패')
  })
})
