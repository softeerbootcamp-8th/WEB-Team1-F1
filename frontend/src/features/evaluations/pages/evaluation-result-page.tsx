import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  CarFront,
  ExternalLink,
  FileCheck2,
  FileText,
  ImagePlus,
  LoaderCircle,
  Phone,
  Save,
  Trash2,
  UploadCloud,
  XCircle,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
  TRANSMISSION_LABEL,
  VEHICLE_KEYWORD_LABEL,
  type VehicleKeyword,
} from '@/features/quote/types'
import { getErrorMessage } from '@/lib/axios'
import { formatNumericInput, parseNumericInput } from '@/lib/input-format'
import { cn } from '@/lib/utils'
import {
  fetchEvaluationDetail,
  patchEvaluationResult,
  rejectEvaluation,
  submitEvaluationResult,
} from '../api'
import {
  MAX_IMAGE_COUNT,
  prepareDocumentFile,
  prepareImageFile,
  uploadPreparedFiles,
} from '@/lib/upload'
import { formatPhone, formatVisitDate, getEvaluationErrorCode } from '../utils'

interface SelectedImage {
  id: string
  file: File | null
  previewUrl: string
  sourceUrl: string | null
}

type SubmitStage = 'idle' | 'issuing' | 'uploading' | 'submitting'

const RESULT_ERROR_MESSAGES: Record<string, string> = {
  EVALUATION_UNMANAGED_DOCUMENT_URL: '진단서 자리에 이미지가 등록됐습니다. PDF 진단서를 다시 선택해 주세요.',
  VEHICLE_UNMANAGED_IMAGE_URL: '사진 자리에 문서나 외부 주소가 등록됐습니다. 사진을 다시 선택해 주세요.',
  EVALUATION_NOT_ASSIGNED_EVALUATOR: '이 방문견적의 담당 평가사만 결과를 제출할 수 있습니다.',
  EVALUATION_EVALUATOR_NOT_ASSIGNED: '아직 평가사가 배정되지 않은 신청입니다.',
  EVALUATION_NOT_DIAGNOSABLE: '반려되어 종료된 신청에는 결과를 제출할 수 없습니다.',
  EVALUATION_RESULT_NOT_SUBMITTED: '평가 결과를 먼저 제출한 뒤 수정해 주세요.',
  EVALUATION_RESULT_LOCKED_BY_AUCTION: '경매에 등록된 차량의 평가 결과는 수정할 수 없습니다.',
  EVALUATION_NOT_REJECTABLE: '이미 완료된 신청은 반려할 수 없습니다.',
}

const VEHICLE_KEYWORDS = Object.keys(VEHICLE_KEYWORD_LABEL) as VehicleKeyword[]
const ACCIDENT_KEYWORD_PAIR: VehicleKeyword[] = ['ACCIDENT_FREE', 'MINOR_EXCHANGE']
const MAX_REJECT_REASON_LENGTH = 500
const WON_PER_MANWON = 10_000

function makeSelectedImage(file: File): SelectedImage {
  return {
    id: `${file.name}-${file.size}-${file.lastModified}-${crypto.randomUUID()}`,
    file,
    previewUrl: URL.createObjectURL(file),
    sourceUrl: null,
  }
}

function makeExistingImage(url: string, index: number): SelectedImage {
  return { id: `existing-${index}-${url}`, file: null, previewUrl: url, sourceUrl: url }
}

function sameValues<T>(left: T[], right: T[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

function normalizeKeywords(keywords: VehicleKeyword[]): VehicleKeyword[] {
  if (!keywords.includes('ACCIDENT_FREE') || !keywords.includes('MINOR_EXCHANGE')) return keywords
  return keywords.filter((keyword) => keyword !== 'MINOR_EXCHANGE')
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
  const [estimatedPriceManwon, setEstimatedPriceManwon] = useState('')
  const [images, setImages] = useState<SelectedImage[]>([])
  const imagesRef = useRef<SelectedImage[]>([])
  const [documentFile, setDocumentFile] = useState<File | null>(null)
  const [keywords, setKeywords] = useState<VehicleKeyword[]>([])
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [stage, setStage] = useState<SubmitStage>('idle')
  const [isDragging, setIsDragging] = useState(false)
  const initializedEvaluationId = useRef<number | null>(null)
  const initialResult = useRef<{
    mileage: number
    estimatedPriceManwon: string
    imageUrls: string[]
    keywords: VehicleKeyword[]
  } | null>(null)

  const detailQuery = useQuery({
    queryKey: ['evaluations', 'detail', evaluationId],
    queryFn: () => fetchEvaluationDetail(evaluationId),
    enabled: Number.isInteger(evaluationId) && evaluationId > 0,
  })

  useEffect(() => {
    if (!detailQuery.data) return
    if (initializedEvaluationId.current === detailQuery.data.evaluationId) return

    const detail = detailQuery.data
    const initialPriceManwon = detail.estimatedPrice === null
      ? ''
      : formatNumericInput(Math.round(detail.estimatedPrice / WON_PER_MANWON))
    initializedEvaluationId.current = detail.evaluationId
    setMileage(detail.mileage === null ? '' : formatNumericInput(detail.mileage))
    setEstimatedPriceManwon(initialPriceManwon)
    setKeywords(normalizeKeywords(detail.keywords))

    if (detail.status === 'APPROVED' && detail.mileage !== null && detail.estimatedPrice !== null) {
      setImages(detail.imageUrls.map(makeExistingImage))
      initialResult.current = {
        mileage: detail.mileage,
        estimatedPriceManwon: initialPriceManwon,
        imageUrls: detail.imageUrls,
        keywords: detail.keywords,
      }
    } else {
      setImages([])
      initialResult.current = null
    }
  }, [detailQuery.data])

  useEffect(() => {
    imagesRef.current = images
  }, [images])

  useEffect(
    () => () => {
      imagesRef.current.forEach(({ file, previewUrl }) => {
        if (file) URL.revokeObjectURL(previewUrl)
      })
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
      if (target?.file) URL.revokeObjectURL(target.previewUrl)
      return current.filter((image) => image.id !== id)
    })
  }

  const toggleKeyword = (keyword: VehicleKeyword, checked: boolean) => {
    setKeywords((current) => {
      if (!checked) return current.filter((candidate) => candidate !== keyword)

      const oppositeKeyword = ACCIDENT_KEYWORD_PAIR.includes(keyword)
        ? ACCIDENT_KEYWORD_PAIR.find((candidate) => candidate !== keyword)
        : undefined
      const selected = current.filter((candidate) => candidate !== oppositeKeyword)
      return VEHICLE_KEYWORDS.filter((candidate) => selected.includes(candidate) || candidate === keyword)
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
      const mileageNumber = parseNumericInput(mileage)
      const priceManwonNumber = parseNumericInput(estimatedPriceManwon)
      if (!Number.isInteger(mileageNumber) || mileageNumber < 1 || mileageNumber > 999_999) {
        throw new Error('주행거리는 1~999,999km 사이의 정수로 입력해 주세요.')
      }
      if (!Number.isSafeInteger(priceManwonNumber) || priceManwonNumber <= 0) {
        throw new Error('산정 시세는 0보다 큰 만원 단위 정수로 입력해 주세요.')
      }
      const priceInWon = priceManwonNumber * WON_PER_MANWON
      if (!Number.isSafeInteger(priceInWon)) throw new Error('산정 시세가 너무 큽니다.')
      if (images.length < 1 || images.length > MAX_IMAGE_COUNT) {
        throw new Error(`차량 사진을 1~${MAX_IMAGE_COUNT}장 등록해 주세요.`)
      }
      // 수정으로 갈지는 status 가 아니라 initialResult 하나로 판정한다. 둘은 같은 뜻이어야
      // 하지만(APPROVED 면 서버가 주행거리·시세를 채워 둔다) 관문과 분기가 서로 다른 값을 보면,
      // 어긋나는 순간 진단서 검사를 건너뛴 채 최초 제출 경로로 떨어진다.
      const initial = initialResult.current
      const currentExistingUrls = images.flatMap(({ sourceUrl }) => sourceUrl ? [sourceUrl] : [])
      const imagesChanged = images.some(({ file }) => file !== null)
        || (initial !== null && !sameValues(currentExistingUrls, initial.imageUrls))

      const uploadImages = async (): Promise<string[]> => {
        const localFiles = images.flatMap(({ file }) => file ? [prepareImageFile(file)] : [])
        const uploadedUrls = localFiles.length > 0 ? await uploadPreparedFiles(localFiles) : []
        let uploadedIndex = 0
        return images.map(({ sourceUrl }) => sourceUrl ?? uploadedUrls[uploadedIndex++])
      }

      if (initial) {
        const request: Parameters<typeof patchEvaluationResult>[1] = {}
        if (mileageNumber !== initial.mileage) request.mileage = mileageNumber
        if (estimatedPriceManwon !== initial.estimatedPriceManwon) {
          request.estimatedPrice = priceInWon
        }
        if (!sameValues(keywords, initial.keywords)) request.keywords = keywords

        if (imagesChanged || documentFile) {
          setStage('issuing')
          setStage('uploading')
          const [imageUrls, documentUrls] = await Promise.all([
            imagesChanged ? uploadImages() : Promise.resolve(undefined),
            documentFile
              ? uploadPreparedFiles([prepareDocumentFile(documentFile)])
              : Promise.resolve(undefined),
          ])
          if (imageUrls) request.imageUrls = imageUrls
          if (documentUrls) request.diagnosticReportUrl = documentUrls[0]
        }

        if (Object.keys(request).length === 0) {
          throw new Error('수정된 항목이 없습니다.')
        }

        setStage('submitting')
        return patchEvaluationResult(evaluationId, request)
      }

      // 최초 제출은 진단서가 반드시 있어야 한다. 이 검사가 분기 안으로 들어와 있어야
      // 아래의 documentFile 이 non-null 로 좁혀지고, ! 단언 없이 컴파일된다.
      if (!documentFile) throw new Error('진단서 PDF를 등록해 주세요.')

      setStage('issuing')
      setStage('uploading')
      // 사진 20장 + 문서 1장이면 서명 요청 상한(20건)을 넘으므로 두 요청으로 나눈다.
      const [imageUrls, [diagnosticReportUrl]] = await Promise.all([
        uploadImages(),
        uploadPreparedFiles([prepareDocumentFile(documentFile)]),
      ])

      setStage('submitting')
      return submitEvaluationResult(evaluationId, {
        mileage: mileageNumber,
        estimatedPrice: priceInWon,
        imageUrls,
        diagnosticReportUrl,
        keywords,
      })
    },
    onSuccess: () => {
      toast.success(detailQuery.data?.status === 'APPROVED'
        ? '평가 결과를 수정했습니다'
        : '평가 결과를 제출했습니다')
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

  const rejectionMutation = useMutation({
    mutationFn: () => {
      const reason = rejectReason.trim()
      if (!reason) throw new Error('반려 사유를 입력해 주세요.')
      if (reason.length > MAX_REJECT_REASON_LENGTH) {
        throw new Error(`반려 사유는 ${MAX_REJECT_REASON_LENGTH}자까지 입력할 수 있습니다.`)
      }
      return rejectEvaluation(evaluationId, reason)
    },
    onSuccess: () => {
      toast.success('방문 결과를 반려했습니다')
      setRejectDialogOpen(false)
      setRejectReason('')
      void queryClient.invalidateQueries({ queryKey: ['evaluations'] })
      navigate('/evaluations/my', { replace: true })
    },
    onError: (error) => {
      const localMessage = !isAxiosError(error) && error instanceof Error ? error.message : null
      toast.error(localMessage ?? getErrorMessage(error, '방문 결과를 반려하지 못했습니다'))
    },
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
          <h1 className="text-3xl font-semibold md:text-4xl">평가 결과 작성</h1>
          <p className="text-muted-foreground mt-3">
            {detail.status === 'APPROVED'
              ? '바꾸려는 항목만 수정하고 기존 사진의 순서도 조정할 수 있습니다.'
              : '주행거리·시세·사진·진단서와 차량 상태를 한 번에 제출합니다.'}
          </p>
        </div>
        <div className="flex items-center gap-3">
          {detail.status === 'APPROVED' && <Badge variant="success">제출 완료 · 수정 가능</Badge>}
          {detail.status === 'REJECTED' && <Badge variant="destructive">반려된 신청</Badge>}
        </div>
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

          {detail.status === 'REQUESTED' && (
            <Button
              type="button"
              variant="destructive"
              className="w-full"
              onClick={() => setRejectDialogOpen(true)}
            >
              <XCircle />반려하기
            </Button>
          )}

          {detail.status === 'REJECTED' && detail.rejectReason && (
            <Card className="border-destructive/20 bg-destructive/5">
              <CardHeader><CardTitle className="text-destructive text-base">반려 사유</CardTitle></CardHeader>
              <CardContent className="whitespace-pre-wrap text-sm">{detail.rejectReason}</CardContent>
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
                <Label htmlFor="evaluation-mileage" className="text-lg font-semibold">실측 주행거리</Label>
                <div className="relative">
                  <Input
                    id="evaluation-mileage"
                    type="text"
                    inputMode="numeric"
                    value={mileage}
                    onChange={(event) => setMileage(formatNumericInput(event.target.value, 6))}
                    placeholder="45,000"
                    maxLength={7}
                    className="h-24 rounded-2xl px-6 pr-20 text-3xl font-semibold tracking-tight placeholder:opacity-40 md:text-3xl"
                    required
                  />
                  <span className="text-muted-foreground pointer-events-none absolute top-1/2 right-6 -translate-y-1/2 text-xl">km</span>
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="evaluation-price" className="text-lg font-semibold">산정 시세</Label>
                <div className="relative">
                  <Input
                    id="evaluation-price"
                    type="text"
                    inputMode="numeric"
                    value={estimatedPriceManwon}
                    onChange={(event) => setEstimatedPriceManwon(formatNumericInput(event.target.value))}
                    placeholder="2,150"
                    maxLength={15}
                    className="h-24 rounded-2xl px-6 pr-24 text-3xl font-semibold tracking-tight placeholder:opacity-40 md:text-3xl"
                    required
                  />
                  <span className="text-muted-foreground pointer-events-none absolute top-1/2 right-6 -translate-y-1/2 text-xl">만원</span>
                </div>
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
                      <p className="min-w-0 flex-1 truncate px-1 text-xs" title={image.file?.name ?? `기존 사진 ${index + 1}`}>
                        {image.file?.name ?? `기존 사진 ${index + 1}`}
                      </p>
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
            <h2 className="text-lg font-semibold">차량 상태 키워드</h2>
            <p className="text-muted-foreground mt-1 text-sm">현장에서 확인한 항목을 모두 선택해 주세요. 해당 사항이 없으면 선택하지 않아도 됩니다.</p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              {VEHICLE_KEYWORDS.map((keyword) => (
                <Label
                  key={keyword}
                  htmlFor={`evaluation-keyword-${keyword}`}
                  className="flex cursor-pointer items-center gap-3 rounded-lg border p-3 font-normal hover:bg-muted/50"
                >
                  <Checkbox
                    id={`evaluation-keyword-${keyword}`}
                    checked={keywords.includes(keyword)}
                    onCheckedChange={(checked) => toggleKeyword(keyword, checked === true)}
                  />
                  {VEHICLE_KEYWORD_LABEL[keyword]}
                </Label>
              ))}
            </div>
          </section>

          <section className="border-t pt-7">
            <h2 className="text-lg font-semibold">진단서</h2>
            <p className="text-muted-foreground mt-1 text-sm">PDF 1개 · 20MB 이하</p>
            {detail.status === 'APPROVED' && detail.diagnosticReportUrl && (
              <Button asChild type="button" variant="outline" className="mt-4 w-full justify-between">
                <a href={detail.diagnosticReportUrl} target="_blank" rel="noreferrer">
                  <span className="flex items-center gap-2"><FileText />기존 진단서 PDF 보기</span>
                  <ExternalLink />
                </a>
              </Button>
            )}
            <div className={detail.status === 'APPROVED' && detail.diagnosticReportUrl ? 'mt-3' : 'mt-4'}>
              <Label htmlFor="diagnostic-report" className="flex cursor-pointer items-center gap-3 rounded-xl border p-4 hover:bg-muted/50">
                {documentFile ? <FileCheck2 className="text-success size-6" /> : <FileText className="text-muted-foreground size-6" />}
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">
                    {documentFile?.name ?? (detail.status === 'APPROVED' ? '새 진단서로 교체' : '진단서 PDF 선택')}
                  </span>
                  <span className="text-muted-foreground mt-0.5 block text-xs">
                    {documentFile
                      ? `${(documentFile.size / 1024 / 1024).toFixed(1)}MB · 저장하면 기존 진단서를 대체합니다.`
                      : detail.status === 'APPROVED' ? '선택하지 않으면 기존 진단서를 유지합니다.' : 'PDF 파일을 선택해 주세요.'}
                  </span>
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

          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={mutation.isPending || cannotSubmit || images.length === 0
              || (detail.status !== 'APPROVED' && !documentFile) || !mileage || !estimatedPriceManwon}
          >
            {mutation.isPending ? <LoaderCircle className="animate-spin" /> : <Save />}
            {detail.status === 'APPROVED' ? '변경한 항목 저장하기' : '평가 결과 제출하기'}
          </Button>
          {cannotSubmit && <p className="text-destructive text-center text-sm">반려되어 종료된 신청에는 결과를 제출할 수 없습니다.</p>}
        </form>
      </div>

      <Dialog
        open={rejectDialogOpen}
        onOpenChange={(open) => !rejectionMutation.isPending && setRejectDialogOpen(open)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>이 방문견적을 반려할까요?</DialogTitle>
            <DialogDescription>
              반려하면 방문견적이 종료되고 판매자에게 사유가 전달됩니다. 반려 후에는 진단 결과를 제출할 수 없습니다.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="evaluation-reject-reason">반려 사유</Label>
            <textarea
              id="evaluation-reject-reason"
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              maxLength={MAX_REJECT_REASON_LENGTH}
              rows={5}
              placeholder="예: 번호판이 등록된 차량과 일치하지 않습니다."
              className="border-input placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-ring/40 w-full resize-none rounded-md border bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-[3px]"
              disabled={rejectionMutation.isPending}
            />
            <p className="text-muted-foreground text-right text-xs">{rejectReason.length}/{MAX_REJECT_REASON_LENGTH}</p>
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={rejectionMutation.isPending} onClick={() => setRejectDialogOpen(false)}>
              취소
            </Button>
            <Button
              variant="destructive"
              disabled={rejectionMutation.isPending || !rejectReason.trim()}
              onClick={() => rejectionMutation.mutate()}
            >
              {rejectionMutation.isPending ? '반려 중…' : '반려 확정'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}
