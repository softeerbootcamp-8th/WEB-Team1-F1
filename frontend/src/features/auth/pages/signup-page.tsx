import { useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { FileCheck2, Upload } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { getErrorMessage } from '@/lib/axios'
import {
  formatPhoneInput,
  getCaretPosition,
  onlyDigits,
  parsePhoneInput,
} from '@/lib/input-format'
import {
  prepareDealerLicenseFile,
  uploadDealerLicense,
  type PreparedDealerLicenseFile,
} from '@/lib/upload'
import { AuthShell } from '../components/auth-shell'
import { RoleSelect } from '../components/role-select'
import { useAuth } from '../auth-context'
import { signUpRequest } from '../api'
import type { SelfSignUpRole } from '@/types/domain'

const INITIAL_FORM = {
  username: '',
  password: '',
  realName: '',
}

const EMAIL_DOMAINS = ['naver.com', 'gmail.com', 'daum.net', 'kakao.com'] as const

export function SignupPage() {
  const { login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState(INITIAL_FORM)
  const [emailLocal, setEmailLocal] = useState('')
  const [emailDomain, setEmailDomain] = useState('')
  const [emailDomainPreset, setEmailDomainPreset] = useState('direct')
  const phoneInputRef = useRef<HTMLInputElement>(null)
  const licenseInputRef = useRef<HTMLInputElement>(null)
  const [role, setRole] = useState<SelfSignUpRole>('GENERAL')
  const [dealerLicense, setDealerLicense] = useState<PreparedDealerLicenseFile | null>(null)
  const [dealerLicenseKey, setDealerLicenseKey] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()

    setIsSubmitting(true)
    try {
      const email = `${emailLocal.trim()}@${emailDomain.trim()}`
      const phone = parsePhoneInput(phoneInputRef.current?.value ?? '')
      let licenseKey: string | undefined
      if (role === 'DEALER') {
        if (!dealerLicense) {
          toast.error('자동차매매사원증을 등록해 주세요')
          return
        }
        licenseKey = dealerLicenseKey ?? (await uploadDealerLicense(dealerLicense))
        setDealerLicenseKey(licenseKey)
      }

      await signUpRequest({
        ...form,
        email,
        phone,
        role,
        ...(licenseKey ? { dealerLicenseKey: licenseKey } : {}),
      })

      // 회원가입은 세션을 발급하지 않아서, 성공 뒤 같은 자격증명으로 다시 로그인해야 한다.
      await login({ username: form.username, password: form.password })

      toast.success('회원가입이 완료되었습니다')
      const returnTo = (
        location.state as {
          returnTo?: { pathname: string; state?: unknown }
        } | null
      )?.returnTo
      if (returnTo?.pathname.startsWith('/') && !returnTo.pathname.startsWith('//')) {
        navigate(returnTo.pathname, { replace: true, state: returnTo.state })
      } else {
        navigate('/', { replace: true })
      }
    } catch (error) {
      toast.error(getErrorMessage(error, '회원가입에 실패했습니다'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="회원가입"
      footer={
        <>
          이미 계정이 있으신가요?{' '}
          <Link
            to="/login"
            state={location.state}
            className="text-foreground font-medium underline-offset-4 hover:underline"
          >
            로그인
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <div className="space-y-2">
          <Label>가입 유형</Label>
          <RoleSelect
            value={role}
            onChange={(nextRole) => {
              setRole(nextRole)
              if (nextRole === 'GENERAL') {
                setDealerLicense(null)
                setDealerLicenseKey(null)
                if (licenseInputRef.current) licenseInputRef.current.value = ''
              }
            }}
          />
        </div>
        {role === 'DEALER' && (
          <div className="space-y-2">
            <Label htmlFor="dealer-license">자동차매매사원증</Label>
            <label
              htmlFor="dealer-license"
              className="hover:bg-accent/50 focus-within:border-ring focus-within:ring-ring/40 flex cursor-pointer items-center gap-3 rounded-lg border border-dashed p-4 transition-colors focus-within:ring-[3px]"
            >
              <span className="bg-muted flex size-10 shrink-0 items-center justify-center rounded-full">
                {dealerLicense ? (
                  <FileCheck2 className="text-success size-5" aria-hidden />
                ) : (
                  <Upload className="text-muted-foreground size-5" aria-hidden />
                )}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">
                  {dealerLicense?.file.name ?? '사원증 파일을 선택해 주세요'}
                </span>
                <span className="text-muted-foreground block text-xs">
                  {dealerLicense
                    ? `${(dealerLicense.file.size / 1024 / 1024).toFixed(1)}MB · 클릭하여 변경`
                    : 'JPG, PNG, PDF · 최대 10MB'}
                </span>
              </span>
              <input
                ref={licenseInputRef}
                id="dealer-license"
                className="sr-only"
                type="file"
                accept="image/jpeg,image/png,application/pdf,.jpg,.jpeg,.png,.pdf"
                required
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  if (!file) return
                  try {
                    setDealerLicense(prepareDealerLicenseFile(file))
                    setDealerLicenseKey(null)
                  } catch (error) {
                    setDealerLicense(null)
                    setDealerLicenseKey(null)
                    event.target.value = ''
                    toast.error(
                      error instanceof Error
                        ? error.message
                        : '자동차매매사원증 파일을 확인해 주세요.',
                    )
                  }
                }}
              />
            </label>
            <p className="text-muted-foreground text-xs">
              제출한 사원증은 딜러 자격 확인 용도로만 안전하게 보관됩니다.
            </p>
          </div>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="username">아이디</Label>
          <Input
            id="username"
            autoComplete="username"
            value={form.username}
            onChange={set('username')}
            pattern="^[a-z0-9_]{4,20}$"
            required
          />
          <p className="text-muted-foreground text-xs">
            영소문자·숫자·밑줄(_) 4~20자
          </p>
        </div>
        <div className="space-y-2">
          <Label htmlFor="realName">이름</Label>
          <Input
            id="realName"
            autoComplete="name"
            value={form.realName}
            onChange={set('realName')}
            minLength={2}
            maxLength={30}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="email-local">이메일</Label>
          <div className="grid grid-cols-[minmax(0,0.9fr)_auto_minmax(0,1fr)_6.5rem] items-center gap-1.5">
            <Input
              id="email-local"
              value={emailLocal}
              onChange={(e) => setEmailLocal(e.target.value.replace(/@/g, ''))}
              autoCapitalize="none"
              autoComplete="off"
              aria-label="이메일 아이디"
              pattern="^[A-Za-z0-9._%+-]+$"
              title="영문, 숫자와 . _ % + - 기호만 사용할 수 있습니다."
              required
            />
            <span className="text-muted-foreground" aria-hidden>
              @
            </span>
            <Input
              value={emailDomain}
              onChange={(e) => {
                setEmailDomain(e.target.value.replace(/@/g, ''))
                setEmailDomainPreset('direct')
              }}
              autoCapitalize="none"
              autoComplete="off"
              aria-label="이메일 도메인"
              placeholder="example.com"
              pattern="^[A-Za-z0-9.-]+[.][A-Za-z]{2,}$"
              title="example.com 형식으로 입력해 주세요."
              required
            />
            <Select
              value={emailDomainPreset}
              onValueChange={(value) => {
                setEmailDomainPreset(value)
                setEmailDomain(value === 'direct' ? '' : value)
              }}
            >
              <SelectTrigger className="w-full" aria-label="이메일 도메인 선택">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="direct">직접 입력</SelectItem>
                {EMAIL_DOMAINS.map((domain) => (
                  <SelectItem key={domain} value={domain}>
                    {domain}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="password">비밀번호</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
            minLength={8}
            maxLength={64}
            required
          />
          <p className="text-muted-foreground text-xs">
            영문·숫자·특수문자 사용 가능, 8~64자(공백 제외)
          </p>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="phone">휴대전화 번호</Label>
          <Input
            ref={phoneInputRef}
            id="phone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            onChange={(e) => {
              e.currentTarget.setCustomValidity('')
              const selectionStart = e.currentTarget.selectionStart ?? e.currentTarget.value.length
              const digitsBeforeCaret = onlyDigits(
                e.currentTarget.value.slice(0, selectionStart),
              ).length
              const nextPhone = formatPhoneInput(e.currentTarget.value)
              const caretPosition = getCaretPosition(nextPhone, digitsBeforeCaret)

              e.currentTarget.value = nextPhone
              e.currentTarget.setSelectionRange(caretPosition, caretPosition)
            }}
            onInvalid={(e) => {
              e.currentTarget.setCustomValidity(
                e.currentTarget.validity.valueMissing
                  ? '휴대전화 번호를 입력해 주세요.'
                  : '010, 011, 016으로 시작하는 올바른 휴대전화 번호를 입력해 주세요.',
              )
            }}
            placeholder="010-1234-5678"
            pattern="^(010|011|016)-[0-9]{3,4}-[0-9]{4}$"
            className="tabular"
            required
          />
          <p className="text-muted-foreground text-xs">
            숫자만 입력해 주세요. 하이픈은 자동으로 입력됩니다.
          </p>
        </div>
        <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? '회원가입 처리 중...' : '회원가입'}
        </Button>
      </form>
    </AuthShell>
  )
}
