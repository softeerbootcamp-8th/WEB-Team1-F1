import { axiosInstance } from '@/lib/axios'

/**
 * 저장소에 올릴 수 있는 형식. 서버 UploadContentType 과 1:1이다.
 *
 * 진단서와 거래 서류가 같은 경로로 발급받으므로 feature 하나에 두지 않는다 —
 * 기능 간 의존을 얕게 두는 규칙에 따라 공용으로 올렸다.
 */
export type UploadContentType =
  | 'image/jpeg'
  | 'image/png'
  | 'image/webp'
  | 'application/pdf'

export interface PresignedUpload {
  key: string
  /** 이 주소로 파일을 PUT 한다. 발급 때 적은 형식·크기와 정확히 같아야 한다 */
  uploadUrl: string
  /** 업로드 후 조회할 주소. 저장해야 하는 값은 이쪽이다 */
  fileUrl: string
  expiresAt: string
}

export interface PresignedUploadRequest {
  files: { contentType: UploadContentType; contentLength: number }[]
}

export interface PresignedUploadResponse {
  uploads: PresignedUpload[]
}

export type DealerLicenseContentType = Exclude<UploadContentType, 'image/webp'>

export interface PreparedDealerLicenseFile {
  file: File
  contentType: DealerLicenseContentType
}

interface PresignedDealerLicense {
  key: string
  uploadUrl: string
  expiresAt: string
}

export async function requestPresignedUploads(
  request: PresignedUploadRequest,
): Promise<PresignedUploadResponse> {
  const { data } = await axiosInstance.post<PresignedUploadResponse>(
    '/api/uploads/presigned',
    request,
  )
  return data
}

export const MAX_IMAGE_COUNT = 20
export const MAX_IMAGE_SIZE = 10 * 1024 * 1024
export const MAX_DOCUMENT_SIZE = 20 * 1024 * 1024
export const MAX_DEALER_LICENSE_SIZE = 10 * 1024 * 1024

const IMAGE_TYPES = new Set<UploadContentType>([
  'image/jpeg',
  'image/png',
  'image/webp',
])

const EXTENSION_TYPES: Record<string, UploadContentType> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  pdf: 'application/pdf',
}

export interface PreparedUploadFile {
  file: File
  contentType: UploadContentType
}

function inferContentType(file: File): UploadContentType | null {
  const declared = file.type.toLowerCase()
  if (IMAGE_TYPES.has(declared as UploadContentType)) {
    return declared as UploadContentType
  }
  if (declared === 'application/pdf') return 'application/pdf'

  const extension = file.name.split('.').pop()?.toLowerCase()
  return extension ? (EXTENSION_TYPES[extension] ?? null) : null
}

export function prepareImageFile(file: File): PreparedUploadFile {
  const contentType = inferContentType(file)
  if (!contentType || !IMAGE_TYPES.has(contentType)) {
    throw new Error(`${file.name}: JPG, PNG, WEBP 이미지만 등록할 수 있습니다.`)
  }
  if (file.size <= 0) throw new Error(`${file.name}: 비어 있는 파일입니다.`)
  if (file.size > MAX_IMAGE_SIZE) {
    throw new Error(`${file.name}: 사진은 장당 10MB까지 등록할 수 있습니다.`)
  }
  return { file, contentType }
}

export function prepareDocumentFile(
  file: File,
  label = '진단서',
): PreparedUploadFile {
  const contentType = inferContentType(file)
  if (contentType !== 'application/pdf') {
    throw new Error(`${label}는 PDF 파일만 등록할 수 있습니다.`)
  }
  if (file.size <= 0) throw new Error(`비어 있는 ${label} 파일입니다.`)
  if (file.size > MAX_DOCUMENT_SIZE) {
    throw new Error(`${label}는 20MB까지 등록할 수 있습니다.`)
  }
  return { file, contentType }
}

export function prepareDealerLicenseFile(file: File): PreparedDealerLicenseFile {
  const contentType = inferContentType(file)
  if (
    contentType !== 'image/jpeg' &&
    contentType !== 'image/png' &&
    contentType !== 'application/pdf'
  ) {
    throw new Error('자동차매매사원증은 JPG, PNG, PDF 파일만 등록할 수 있습니다.')
  }
  if (file.size <= 0) throw new Error('비어 있는 자동차매매사원증 파일입니다.')
  if (file.size > MAX_DEALER_LICENSE_SIZE) {
    throw new Error('자동차매매사원증은 10MB까지 등록할 수 있습니다.')
  }
  return { file, contentType }
}

/** 회원가입 전에 비공개 영역으로 사원증 한 건을 업로드하고 가입 요청용 키를 돌려준다. */
export async function uploadDealerLicense(
  prepared: PreparedDealerLicenseFile,
): Promise<string> {
  const { file, contentType } = prepared
  const { data } = await axiosInstance.post<PresignedDealerLicense>(
    '/api/uploads/dealer-license/presigned',
    { contentType, contentLength: file.size },
  )

  const response = await fetch(data.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: file,
  })
  if (!response.ok) {
    throw new Error('자동차매매사원증 업로드에 실패했습니다. 다시 시도해 주세요.')
  }

  return data.key
}

/**
 * 발급받은 주소로 파일 한 건을 올린다.
 *
 * uploadUrl 에는 공용 axios 의 baseURL·쿠키가 붙으면 안 되므로 의도적으로 순수 fetch 만 쓴다.
 * 발급 경로가 여럿(공용·거래 서류)이라 이 부분만 따로 두었다 — 헤더나 크기 규칙이 갈라지면
 * 서명이 깨져 S3 가 거부한다.
 */
export async function putPreparedFile(
  { file, contentType }: PreparedUploadFile,
  uploadUrl: string,
): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: file,
  })
  if (!response.ok) {
    throw new Error(`${file.name} 업로드에 실패했습니다. 다시 시도해 주세요.`)
  }
}

/**
 * 차량 평가 경로의 일괄 업로드. 발급이 평가사 전용이라 다른 역할은 부를 수 없다 —
 * 판매자가 내는 거래 서류는 거래 아래의 전용 발급을 쓴다.
 */
export async function uploadPreparedFiles(
  preparedFiles: PreparedUploadFile[],
): Promise<string[]> {
  if (preparedFiles.length === 0) return []
  if (preparedFiles.length > MAX_IMAGE_COUNT) {
    throw new Error(`한 번에 ${MAX_IMAGE_COUNT}개 파일까지 업로드할 수 있습니다.`)
  }

  const { uploads } = await requestPresignedUploads({
    files: preparedFiles.map(({ file, contentType }) => ({
      contentType,
      contentLength: file.size,
    })),
  })

  if (uploads.length !== preparedFiles.length) {
    throw new Error('업로드 주소를 모두 발급받지 못했습니다. 다시 시도해 주세요.')
  }

  await Promise.all(
    uploads.map(({ uploadUrl }, index) => putPreparedFile(preparedFiles[index], uploadUrl)),
  )

  return uploads.map(({ fileUrl }) => fileUrl)
}
