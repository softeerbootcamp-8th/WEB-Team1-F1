import { requestPresignedUploads } from './api'
import type { UploadContentType } from './types'

export const MAX_IMAGE_COUNT = 20
export const MAX_IMAGE_SIZE = 10 * 1024 * 1024
export const MAX_DOCUMENT_SIZE = 20 * 1024 * 1024

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

export function prepareDocumentFile(file: File): PreparedUploadFile {
  const contentType = inferContentType(file)
  if (contentType !== 'application/pdf') {
    throw new Error('진단서는 PDF 파일만 등록할 수 있습니다.')
  }
  if (file.size <= 0) throw new Error('비어 있는 진단서 파일입니다.')
  if (file.size > MAX_DOCUMENT_SIZE) {
    throw new Error('진단서는 20MB까지 등록할 수 있습니다.')
  }
  return { file, contentType }
}

/**
 * 서명 발급 후 S3로 직접 업로드한다. uploadUrl에는 공용 axios의 baseURL·쿠키가
 * 붙으면 안 되므로 의도적으로 순수 fetch만 사용한다.
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
    uploads.map(async ({ uploadUrl }, index) => {
      const { file, contentType } = preparedFiles[index]
      const response = await fetch(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': contentType },
        body: file,
      })
      if (!response.ok) {
        throw new Error(`${file.name} 업로드에 실패했습니다. 다시 시도해 주세요.`)
      }
    }),
  )

  return uploads.map(({ fileUrl }) => fileUrl)
}
