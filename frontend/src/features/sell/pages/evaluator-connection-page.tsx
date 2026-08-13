import { useCallback, useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, LoaderCircle } from 'lucide-react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/features/auth/auth-context'
import { requestVisitQuote } from '@/features/sell/api'
import type {
  VisitQuoteRequest,
  VisitQuoteResponse,
} from '@/features/sell/types'
import type { VehicleOwnerValues } from '@/features/vehicle/components/vehicle-owner-form'
import { VehicleSummary } from '@/features/vehicle/components/vehicle-summary'
import type { VehicleLookupResponse } from '@/features/vehicle/types'
import { getErrorMessage } from '@/lib/axios'
import { formatPhoneInput, parsePhoneInput } from '@/lib/input-format'

const CONTACT_PHONE_PATTERN = /^01\d{8,9}$/
const VISIT_QUOTE_FIELDS: (keyof VisitQuoteRequest)[] = [
  'plateNumber',
  'ownerName',
  'visitAddress',
  'visitDate',
  'contactPhone',
]

interface VisitQuoteDraft {
  visitAddress: string
  visitDate: string | null
  contactPhone: string
}

interface EvaluatorConnectionState extends VehicleOwnerValues {
  vehicle: VehicleLookupResponse
  draft?: VisitQuoteDraft
}

interface VisitQuoteProblemDetail {
  code?: string
  detail?: string
  errors?: { field: string; message: string }[]
}

type FieldErrors = Partial<Record<keyof VisitQuoteRequest, string>>

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseLocalDate(value: string | null | undefined) {
  if (!value) return null
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatVisitDate(value: string) {
  return parseLocalDate(value)?.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })
}

function isVisitQuoteField(field: string): field is keyof VisitQuoteRequest {
  return VISIT_QUOTE_FIELDS.includes(field as keyof VisitQuoteRequest)
}

export function EvaluatorConnectionPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, isLoading } = useAuth()
  const pageState = location.state as EvaluatorConnectionState | null
  const ownerName = pageState?.ownerName ?? ''
  const plateNumber = pageState?.plateNumber ?? ''
  const vehicle = pageState?.vehicle
  const hasVehicle = Boolean(
    ownerName && plateNumber && vehicle?.plateNumber === plateNumber,
  )

  const today = useMemo(() => {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    return date
  }, [])
  const [visitDate, setVisitDate] = useState<Date | null>(() =>
    parseLocalDate(pageState?.draft?.visitDate),
  )
  const [visitAddress, setVisitAddress] = useState(
    pageState?.draft?.visitAddress ?? '',
  )
  const [contactPhone, setContactPhone] = useState(
    formatPhoneInput(pageState?.draft?.contactPhone ?? ''),
  )
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [result, setResult] = useState<VisitQuoteResponse | null>(null)

  // 로그인 왕복에서 돌아올 자리. 안내 화면의 링크와 세션 만료 리다이렉트가 같은 값을 쓴다 —
  // 두 벌로 두면 한쪽만 draft를 빠뜨려도 티가 안 난다
  const loginReturnTo = useMemo(
    () => ({
      pathname: '/sell/evaluator',
      state: {
        ownerName,
        plateNumber,
        vehicle: vehicle!,
        draft: {
          visitAddress,
          visitDate: visitDate ? formatLocalDate(visitDate) : null,
          contactPhone,
        },
      } satisfies EvaluatorConnectionState,
    }),
    [contactPhone, ownerName, plateNumber, vehicle, visitAddress, visitDate],
  )

  // 작성 중 세션이 끊긴 경우에만 쓴다. 이때는 입력값을 들고 로그인으로 갔다 돌아오는 편이
  // 폼을 그대로 둔 채 실패를 알리는 것보다 낫다 — 다시 눌러도 또 401 이다
  const redirectToLogin = useCallback(() => {
    navigate('/login', { replace: true, state: { returnTo: loginReturnTo } })
  }, [loginReturnTo, navigate])

  const mutation = useMutation({
    mutationFn: requestVisitQuote,
    onSuccess: (response) => {
      setResult(response)
      toast.success('방문견적 신청이 접수되었습니다')
    },
    onError: (error) => {
      const response = isAxiosError<VisitQuoteProblemDetail>(error)
        ? error.response
        : undefined
      const body = response?.data

      if (body?.code === 'INVALID_REQUEST') {
        const nextErrors: FieldErrors = {}
        body.errors?.forEach(({ field, message }) => {
          if (isVisitQuoteField(field) && !nextErrors[field]) {
            nextErrors[field] = message
          }
        })
        setFieldErrors(nextErrors)
        toast.error(getErrorMessage(error, '입력값을 확인해 주세요'))
        return
      }

      if (body?.code === 'EVALUATION_PAST_VISIT_DATE') {
        const message = getErrorMessage(error, '방문 희망 날짜를 확인해 주세요')
        setFieldErrors((current) => ({ ...current, visitDate: message }))
        toast.error(message)
        return
      }

      if (body?.code === 'EVALUATION_VEHICLE_NOT_FOUND') {
        toast.error(
          getErrorMessage(error, '차량 번호판과 소유자 이름을 확인해 주세요'),
        )
        navigate('/sell', {
          replace: true,
          state: {
            ownerName,
            plateNumber,
          } satisfies VehicleOwnerValues,
        })
        return
      }

      if (body?.code === 'EVALUATION_DUPLICATE_REQUEST') {
        toast.error('이미 진행 중인 신청이 있습니다')
        return
      }

      if (body?.code === 'AUTH_UNAUTHENTICATED' || response?.status === 401) {
        toast.error(getErrorMessage(error, '로그인이 필요합니다'))
        redirectToLogin()
        return
      }

      toast.error(getErrorMessage(error, '방문견적 신청에 실패했습니다'))
    },
  })

  if (!hasVehicle) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="차량 정보를 먼저 입력해 주세요"
          description="내 차 팔기에서 이름과 번호판을 입력하면 평가사를 연결할 수 있습니다."
          action={
            <Button asChild>
              <Link to="/sell">내 차 팔기로</Link>
            </Button>
          }
        />
      </main>
    )
  }

  if (isLoading) {
    return (
      <main
        className="flex min-h-80 items-center justify-center"
        aria-label="로그인 확인 중"
      >
        <LoaderCircle className="text-primary size-6 animate-spin" />
      </main>
    )
  }

  // 로그인 화면으로 밀어내지 않고 이유를 이 자리에 남긴다. 경매방·경매 목록·마이페이지가
  // 쓰는 것과 같은 형태다 — 말없이 이동하면 잘못 눌렀거나 오류가 난 것으로 읽힌다
  if (!isAuthenticated) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="평가사 연결">
        <EmptyState
          title="로그인이 필요합니다"
          description="방문견적은 예약자 확인이 필요해 로그인 후 신청할 수 있습니다."
          action={
            <Button asChild>
              {/* 히스토리를 늘리지 않는다. 쌓아 두면 로그인을 마친 뒤의 "뒤로"가
                  이 안내 화면으로 돌아오는데, 그때는 이미 볼 이유가 없는 화면이다 */}
              <Link to="/login" replace state={{ returnTo: loginReturnTo }}>
                로그인
              </Link>
            </Button>
          }
        />
      </main>
    )
  }

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!visitDate) {
      setFieldErrors((current) => ({
        ...current,
        visitDate: '방문 희망 날짜를 선택해 주세요.',
      }))
      return
    }

    setFieldErrors({})
    mutation.mutate({
      plateNumber,
      ownerName: ownerName.trim(),
      visitAddress: visitAddress.trim(),
      visitDate: formatLocalDate(visitDate),
      contactPhone: parsePhoneInput(contactPhone),
    })
  }

  const requestComplete = result?.status === 'REQUESTED'

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="평가사 연결">
      {!requestComplete && (
        <Button asChild variant="ghost" size="sm" className="-ml-2">
          <Link to="/sell" state={{ ownerName, plateNumber, vehicle }}>
            <ArrowLeft className="size-4" />
            차량 정보 수정
          </Link>
        </Button>
      )}

      <header className="mt-6 max-w-2xl">
        <h1 className="text-3xl font-semibold md:text-4xl lg:text-5xl">
          {requestComplete
            ? '방문견적 신청이 접수됐어요.'
            : '평가사 방문 정보를 입력해 주세요.'}
        </h1>
      </header>

      <div className="mt-12 grid gap-8 lg:grid-cols-[1.2fr_0.8fr]">
        <aside className="rounded-2xl border p-7 md:p-8">
          <VehicleSummary vehicle={vehicle!} />
        </aside>

        <section className="min-w-0 self-start rounded-2xl border p-7 md:p-8">
          {requestComplete ? (
            <>
              <dl className="space-y-5 text-sm" aria-live="polite">
                <div>
                  <dt className="text-muted-foreground">방문 희망 날짜</dt>
                  <dd className="mt-1 font-medium">{formatVisitDate(result.visitDate)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">방문 주소</dt>
                  <dd className="mt-1 font-medium">{result.visitAddress}</dd>
                </div>
              </dl>
            </>
          ) : (
              <form className="space-y-6" onSubmit={submit}>
                <div>
                  <Label htmlFor="visit-date">방문 희망 날짜</Label>
                  <Input
                    id="visit-date"
                    type="date"
                    className="mt-3"
                    min={formatLocalDate(today)}
                    value={visitDate ? formatLocalDate(visitDate) : ''}
                    onChange={(event) => {
                      setVisitDate(parseLocalDate(event.target.value))
                      setFieldErrors((current) => ({
                        ...current,
                        visitDate: undefined,
                      }))
                    }}
                    aria-invalid={Boolean(fieldErrors.visitDate)}
                    required
                  />
                  {fieldErrors.visitDate && (
                    <p className="text-destructive mt-2 text-xs" role="alert">
                      {fieldErrors.visitDate}
                    </p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="visit-address">방문 주소</Label>
                  <Input
                    id="visit-address"
                    value={visitAddress}
                    onChange={(event) => {
                      setVisitAddress(event.target.value)
                      setFieldErrors((current) => ({
                        ...current,
                        visitAddress: undefined,
                      }))
                    }}
                    placeholder="서울 성동구 왕십리로 83"
                    autoComplete="street-address"
                    maxLength={200}
                    aria-invalid={Boolean(fieldErrors.visitAddress)}
                    required
                  />
                  {fieldErrors.visitAddress && (
                    <p className="text-destructive text-xs" role="alert">
                      {fieldErrors.visitAddress}
                    </p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="contact-phone">연락처</Label>
                  <Input
                    id="contact-phone"
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel-national"
                    value={contactPhone}
                    onChange={(event) => {
                      setContactPhone(formatPhoneInput(event.target.value))
                      setFieldErrors((current) => ({
                        ...current,
                        contactPhone: undefined,
                      }))
                    }}
                    placeholder="010-1234-5678"
                    pattern="^01\d-\d{3,4}-\d{4}$"
                    maxLength={13}
                    aria-invalid={Boolean(fieldErrors.contactPhone)}
                    required
                  />
                  {fieldErrors.contactPhone && (
                    <p className="text-destructive text-xs" role="alert">
                      {fieldErrors.contactPhone}
                    </p>
                  )}
                </div>

                {(fieldErrors.ownerName || fieldErrors.plateNumber) && (
                  <p className="text-destructive text-sm" role="alert">
                    {fieldErrors.ownerName ?? fieldErrors.plateNumber}
                  </p>
                )}

                <Button
                  type="submit"
                  size="lg"
                  className="w-full"
                  disabled={
                    mutation.isPending ||
                    !visitDate ||
                    !visitAddress.trim() ||
                    !CONTACT_PHONE_PATTERN.test(parsePhoneInput(contactPhone))
                  }
                >
                  {mutation.isPending && (
                    <LoaderCircle className="size-4 animate-spin" />
                  )}
                  예약하기
                </Button>
              </form>
          )}
        </section>
      </div>
    </main>
  )
}
