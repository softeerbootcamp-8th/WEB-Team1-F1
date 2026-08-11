import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  CarFront,
  FileCheck2,
  FileText,
  ImagePlus,
  LoaderCircle,
  Phone,
  Save,
  Trash2,
  UploadCloud,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL, TRANSMISSION_LABEL } from '@/features/quote/types'
import { getErrorMessage } from '@/lib/axios'
import { formatKRW, formatMileage } from '@/lib/format'
import { cn } from '@/lib/utils'
import { fetchEvaluationDetail, submitEvaluationResult } from '../api'
import {
  MAX_IMAGE_COUNT,
  prepareDocumentFile,
  prepareImageFile,
  uploadPreparedFiles,
} from '@/lib/upload'
import { formatPhone, formatVisitDate, getEvaluationErrorCode } from '../utils'

interface SelectedImage {
  id: string
  file: File
  previewUrl: string
}

type SubmitStage = 'idle' | 'issuing' | 'uploading' | 'submitting'

const RESULT_ERROR_MESSAGES: Record<string, string> = {
  EVALUATION_UNMANAGED_DOCUMENT_URL: '진단서 자리에 이미지가 등록됐습니다. PDF 진단서를 다시 선택해 주세요.',
  VEHICLE_UNMANAGED_IMAGE_URL: '사진 자리에 문서나 외부 주소가 등록됐습니다. 사진을 다시 선택해 주세요.',
  EVALUATION_NOT_ASSIGNED_EVALUATOR: '이 방문견적의 담당 평가사만 결과를 제출할 수 있습니다.',
  EVALUATION_EVALUATOR_NOT_ASSIGNED: '아직 평가사가 배정되지 않은 신청입니다.',
  EVALUATION_NOT_DIAGNOSABLE: '반려되어 종료된 신청에는 결과를 제출할 수 없습니다.',
}

function makeSelectedImage(file: File): SelectedImage {
  return {
    id: `${file.name}-${file.size}-${file.lastModified}-${crypto.randomUUID()}`,
    file,
    previewUrl: URL.createObjectURL(file),
  }
}

function stageMeta(stage: SubmitStage) {
  if (stage === 'issuing') return { value: 20, label: '업로드 주소를 준비하고 있습니다' }
  if (stage === 'uploading') return { value: 55, label: '사진과 진단서를 업로드하고 있습니다' }
  if (stage === 'submitting') return { value: 85, label: '평가 결과를 저장하고 있습니다' }
  return { value: 0, label: '' }
}

export function EvaluationResultPage() {
  const { evaluationId: evaluationIdParam } = useParams()
  const evaluationId = Number(evaluationIdParam)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [mileage, setMileage] = useState('')
  const [estimatedPrice, setEstimatedPrice] = useState('')
  const [images, setImages] = useState<SelectedImage[]>([])
  const imagesRef = useRef<SelectedImage[]>([])
  const [documentFile, setDocumentFile] = useState<File | null>(null)
  const [stage, setStage] = useState<SubmitStage>('idle')
  const [isDragging, setIsDragging] = useState(false)

  const detailQuery = useQuery({
    queryKey: ['evaluations', 'detail', evaluationId],
    queryFn: () => fetchEvaluationDetail(evaluationId),
    enabled: Number.isInteger(evaluationId) && evaluationId > 0,
  })

  useEffect(() => {
    if (!detailQuery.data) return
    setMileage(detailQuery.data.mileage?.toString() ?? '')
    setEstimatedPrice(detailQuery.data.estimatedPrice?.toString() ?? '')
  }, [detailQuery.data])

  useEffect(() => {
    imagesRef.current = images
  }, [images])

  useEffect(
    () => () => {
      imagesRef.current.forEach(({ previewUrl }) => URL.revokeObjectURL(previewUrl))
    },
    [],
  )

  const addImages = (files: File[]) => {
    if (images.length + files.length > MAX_IMAGE_COUNT) {
      toast.error(`사진은 최대 ${MAX_IMAGE_COUNT}장까지 등록할 수 있습니다.`)
      return
    }
    try {
      files.forEach(prepareImageFile)
      setImages((current) => [...current, ...files.map(makeSelectedImage)])
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '사진을 확인해 주세요.')
    }
  }

  const removeImage = (id: string) => {
    setImages((current) => {
      const target = current.find((image) => image.id === id)
      if (target) URL.revokeObjectURL(target.previewUrl)
      return current.filter((image) => image.id !== id)
    })
  }

  const moveImage = (index: number, direction: -1 | 1) => {
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= images.length) return
    setImages((current) => {
      const next = [...current]
      ;[next[index], next[nextIndex]] = [next[nextIndex], next[index]]
      return next
    })
  }

  const mutation = useMutation({
    mutationFn: async () => {
      const mileageNumber = Number(mileage)
      const priceNumber = Number(estimatedPrice)
      if (!Number.isInteger(mileageNumber) || mileageNumber < 1 || mileageNumber > 999_999) {
        throw new Error('주행거리는 1~999,999km 사이의 정수로 입력해 주세요.')
      }
      if (!Number.isSafeInteger(priceNumber) || priceNumber <= 0) {
        throw new Error('산정 시세는 0보다 큰 원 단위 정수로 입력해 주세요.')
      }
      if (images.length < 1 || images.length > MAX_IMAGE_COUNT) {
        throw new Error(`차량 사진을 1~${MAX_IMAGE_COUNT}장 등록해 주세요.`)
      }
      if (!documentFile) throw new Error('진단서 PDF를 등록해 주세요.')

      const preparedImages = images.map(({ file }) => prepareImageFile(file))
      const preparedDocument = prepareDocumentFile(documentFile)

      setStage('issuing')
      setStage('uploading')
      // 사진 20장 + 문서 1장이면 서명 요청 상한(20건)을 넘으므로 두 요청으로 나눈다.
      const [imageUrls, [diagnosticReportUrl]] = await Promise.all([
        uploadPreparedFiles(preparedImages),
        uploadPreparedFiles([preparedDocument]),
      ])

      setStage('submitting')
      return submitEvaluationResult(evaluationId, {
        mileage: mileageNumber,
        estimatedPrice: priceNumber,
        imageUrls,
        diagnosticReportUrl,
      })
    },
    onSuccess: () => {
      toast.success('평가 결과를 제출했습니다')
      void queryClient.invalidateQueries({ queryKey: ['evaluations'] })
      navigate('/evaluations/my', { replace: true })
    },
    onError: (error) => {
      const code = getEvaluationErrorCode(error)
      const localMessage = !isAxiosError(error) && error instanceof Error ? error.message : null
      toast.error(
        (code && RESULT_ERROR_MESSAGES[code]) ??
          localMessage ??
          getErrorMessage(error, '평가 결과를 제출하지 못했습니다'),
      )
    },
    onSettled: () => setStage('idle'),
  })

  const detail = detailQuery.data
  const progress = useMemo(() => stageMeta(stage), [stage])
  const isInvalidId = !Number.isInteger(evaluationId) || evaluationId <= 0

  if (isInvalidId) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState title="잘못된 방문견적 번호입니다" action={<Button asChild><Link to="/evaluations/my">내 담당 목록</Link></Button>} />
      </main>
    )
  }

  if (detailQuery.isLoading) {
    return <main className="flex min-h-[60vh] items-center justify-center"><LoaderCircle className="size-7 animate-spin" aria-label="상세 불러오는 중" /></main>
  }

  if (detailQuery.isError || !detail) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="방문견적 상세를 불러오지 못했습니다"
          description={getErrorMessage(detailQuery.error, '존재하지 않거나 조회 권한이 없는 신청입니다.')}
          action={<Button asChild><Link to="/evaluations/my">내 담당 목록</Link></Button>}
        />
      </main>
    )
  }

  const cannotSubmit = detail.status === 'REJECTED'

  return (
    <main className="mx-auto max-w-6xl px-6 py-12" aria-label="평가 결과 작성">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/evaluations/my"><ArrowLeft />내 담당 목록</Link>
      </Button>

      <header className="mt-6 flex flex-wrap items-end justify-between gap-5">
        <div>
          <p className="text-muted-foreground text-sm tracking-[0.15em] uppercase">Diagnosis</p>
          <h1 className="mt-2 text-3xl font-semibold md:text-4xl">평가 결과 작성</h1>
          <p className="text-muted-foreground mt-3">
            다시 제출하면 기존 주행거리·시세·사진·진단서가 모두 교체됩니다.
          </p>
        </div>
        {detail.status === 'APPROVED' && <Badge variant="success">제출 완료 · 수정 가능</Badge>}
        {detail.status === 'REJECTED' && <Badge variant="destructive">반려된 신청</Badge>}
      </header>

      <div className="mt-8 grid gap-7 lg:grid-cols-[0.72fr_1.28fr]">
        <aside className="space-y-5">
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><CarFront />차량·방문 정보</CardTitle></CardHeader>
            <CardContent className="space-y-5 text-sm">
              <div>
                <p className="text-lg font-semibold">{MANUFACTURER_LABEL[detail.manufacturer]} {detail.model}</p>
                <p className="text-muted-foreground mt-1">{detail.modelYear}년식 · {detail.plateNumber}</p>
                <p className="text-muted-foreground mt-1">{FUEL_TYPE_LABEL[detail.fuelType]} · {TRANSMISSION_LABEL[detail.transmission]}</p>
              </div>
              <dl className="space-y-4 border-t pt-5">
                <div><dt className="text-muted-foreground">방문 희망일</dt><dd className="mt-1 font-medium">{formatVisitDate(detail.visitDate)}</dd></div>
                <div><dt className="text-muted-foreground">방문 주소</dt><dd className="mt-1 font-medium">{detail.visitAddress}</dd></div>
                <div><dt className="text-muted-foreground">판매자 연락처</dt><dd className="mt-1"><a className="inline-flex items-center gap-2 font-semibold underline-offset-4 hover:underline" href={`tel:${detail.contactPhone}`}><Phone className="size-4" />{formatPhone(detail.contactPhone)}</a></dd></div>
              </dl>
            </CardContent>
          </Card>

          {detail.status === 'APPROVED' && detail.mileage && detail.estimatedPrice && (
            <Card>
              <CardHeader><CardTitle className="text-base">현재 제출 결과</CardTitle></CardHeader>
              <CardContent className="space-y-2 text-sm">
                <p>{formatMileage(detail.mileage)}</p>
                <p className="text-lg font-semibold">{formatKRW(detail.estimatedPrice)}</p>
                <div className="pt-2">
                  <p className="text-muted-foreground mb-2">
                    차량 사진 {detail.imageUrls.length}장
                  </p>
                  <div className="grid max-h-72 grid-cols-2 gap-2 overflow-y-auto pr-1">
                    {detail.imageUrls.map((url, index) => (
                      <a
                        key={`${url}-${index}`}
                        href={url}
                        target="_blank"
                        rel="noreferrer"
                        className="group relative aspect-video overflow-hidden rounded-lg bg-muted"
                        aria-label={`기존 제출 사진 ${index + 1} 원본 보기`}
                      >
                        <img
                          src={url}
                          alt={`기존 제출 차량 사진 ${index + 1}`}
                          className="size-full object-cover transition-transform group-hover:scale-105"
                        />
                        <Badge
                          variant={index === 0 ? 'default' : 'secondary'}
                          className="absolute top-1.5 left-1.5"
                        >
                          {index === 0 ? '대표' : index + 1}
                        </Badge>
                      </a>
                    ))}
                  </div>
                </div>
                {detail.diagnosticReportUrl && <a className="inline-flex items-center gap-2 underline underline-offset-4" href={detail.diagnosticReportUrl} target="_blank" rel="noreferrer"><FileText className="size-4" />기존 진단서 보기</a>}
              </CardContent>
            </Card>
          )}
        </aside>

        <form
          className="space-y-7 rounded-2xl border p-6 md:p-8"
          onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}
        >
          <section>
            <h2 className="text-lg font-semibold">진단 수치</h2>
            <div className="mt-4 grid gap-5 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="evaluation-mileage">실측 주행거리 (km)</Label>
                <Input id="evaluation-mileage" type="number" min={1} max={999999} step={1} inputMode="numeric" value={mileage} onChange={(event) => setMileage(event.target.value)} placeholder="예: 45000" required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="evaluation-price">산정 시세 (원)</Label>
                <Input id="evaluation-price" type="number" min={1} step={1} inputMode="numeric" value={estimatedPrice} onChange={(event) => setEstimatedPrice(event.target.value)} placeholder="예: 21500000" required />
              </div>
            </div>
          </section>

          <section className="border-t pt-7">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div><h2 className="text-lg font-semibold">차량 사진</h2><p className="text-muted-foreground mt-1 text-sm">1~20장 · JPG, PNG, WEBP · 장당 10MB 이하</p></div>
              <Badge variant="outline">{images.length}/{MAX_IMAGE_COUNT}</Badge>
            </div>
            <label
              htmlFor="evaluation-images"
              onDragEnter={() => setIsDragging(true)}
              onDragLeave={() => setIsDragging(false)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => { event.preventDefault(); setIsDragging(false); addImages(Array.from(event.dataTransfer.files)) }}
              className={cn('mt-4 flex cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed px-4 py-10 text-center transition-colors', isDragging ? 'border-primary bg-muted' : 'hover:bg-muted/50')}
            >
              <UploadCloud className="text-muted-foreground size-7" />
              <span className="mt-3 text-sm font-medium">사진을 끌어 놓거나 눌러서 선택</span>
              <span className="text-muted-foreground mt-1 text-xs">선택 순서대로 표시되며 첫 장이 대표 이미지입니다.</span>
              <input id="evaluation-images" className="sr-only" type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" multiple onChange={(event) => { addImages(Array.from(event.target.files ?? [])); event.target.value = '' }} />
            </label>

            {images.length > 0 && (
              <ol className="mt-5 grid gap-3 sm:grid-cols-2">
                {images.map((image, index) => (
                  <li key={image.id} className="overflow-hidden rounded-xl border">
                    <div className="relative aspect-video bg-muted">
                      <img src={image.previewUrl} alt={`차량 사진 ${index + 1}`} className="size-full object-cover" />
                      <Badge className="absolute top-2 left-2" variant={index === 0 ? 'default' : 'secondary'}>{index === 0 ? '대표' : index + 1}</Badge>
                    </div>
                    <div className="flex items-center gap-1 p-2">
                      <p className="min-w-0 flex-1 truncate px-1 text-xs" title={image.file.name}>{image.file.name}</p>
                      <Button type="button" variant="ghost" size="icon" className="size-8" disabled={index === 0} onClick={() => moveImage(index, -1)} aria-label="앞으로 이동"><ArrowUp /></Button>
                      <Button type="button" variant="ghost" size="icon" className="size-8" disabled={index === images.length - 1} onClick={() => moveImage(index, 1)} aria-label="뒤로 이동"><ArrowDown /></Button>
                      <Button type="button" variant="ghost" size="icon" className="text-destructive size-8" onClick={() => removeImage(image.id)} aria-label="사진 삭제"><Trash2 /></Button>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section className="border-t pt-7">
            <h2 className="text-lg font-semibold">진단서</h2>
            <p className="text-muted-foreground mt-1 text-sm">PDF 1개 · 20MB 이하</p>
            <div className="mt-4">
              <Label htmlFor="diagnostic-report" className="flex cursor-pointer items-center gap-3 rounded-xl border p-4 hover:bg-muted/50">
                {documentFile ? <FileCheck2 className="text-success size-6" /> : <FileText className="text-muted-foreground size-6" />}
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{documentFile?.name ?? '진단서 PDF 선택'}</span>
                  {documentFile && <span className="text-muted-foreground mt-0.5 block text-xs">{(documentFile.size / 1024 / 1024).toFixed(1)}MB</span>}
                </span>
                <ImagePlus className="size-4" />
              </Label>
              <input id="diagnostic-report" className="sr-only" type="file" accept="application/pdf,.pdf" onChange={(event) => { const file = event.target.files?.[0]; if (!file) return; try { prepareDocumentFile(file); setDocumentFile(file) } catch (error) { toast.error(error instanceof Error ? error.message : '진단서를 확인해 주세요.'); event.target.value = '' } }} />
            </div>
          </section>

          {mutation.isPending && (
            <div className="bg-muted/50 rounded-xl p-4" aria-live="polite">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium"><LoaderCircle className="size-4 animate-spin" />{progress.label}</div>
              <Progress value={progress.value} />
            </div>
          )}

          <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending || cannotSubmit || images.length === 0 || !documentFile || !mileage || !estimatedPrice}>
            {mutation.isPending ? <LoaderCircle className="animate-spin" /> : <Save />}
            {detail.status === 'APPROVED' ? '평가 결과 교체하기' : '평가 결과 제출하기'}
          </Button>
          {cannotSubmit && <p className="text-destructive text-center text-sm">반려되어 종료된 신청에는 결과를 제출할 수 없습니다.</p>}
        </form>
      </div>
    </main>
  )
}
