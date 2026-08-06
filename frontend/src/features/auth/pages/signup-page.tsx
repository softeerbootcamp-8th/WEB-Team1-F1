import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { getErrorMessage } from '@/lib/axios'
import { markJustSignedUp } from '@/lib/signup-welcome'
import { AuthShell } from '../components/auth-shell'
import { RoleSelect } from '../components/role-select'
import { useAuth } from '../auth-context'
import { signUpRequest } from '../api'
import type { SelfSignUpRole } from '@/types/domain'

const INITIAL_FORM = {
  username: '',
  email: '',
  password: '',
  realName: '',
}

const onlyDigits = (value: string) => value.replace(/\D/g, '')

export function SignupPage() {
  const { login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState(INITIAL_FORM)
  const [phone, setPhone] = useState({ area: '', middle: '', last: '' })
  const [role, setRole] = useState<SelfSignUpRole>('GENERAL')
  const [agree, setAgree] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const setPhonePart = (key: keyof typeof phone, maxLength: number) =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setPhone((prev) => ({ ...prev, [key]: onlyDigits(e.target.value).slice(0, maxLength) }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!agree) {
      toast.error('약관에 동의해 주세요')
      return
    }

    setIsSubmitting(true)
    try {
      const phoneNumber = `${phone.area}-${phone.middle}-${phone.last}`
      const signedUpUser = await signUpRequest({ ...form, phone: phoneNumber, role })

      // 환영 알림은 발행 시점에 구독이 없어 실시간으로 도착하지 못한다.
      // 표시를 남겨 두면 알림 쪽이 이번 한 번만 대신 안내한다.
      markJustSignedUp(signedUpUser.id)

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
      subtitle="30초 만에 시작하기"
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
          <RoleSelect value={role} onChange={setRole} />
        </div>
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
          <p className="text-muted-foreground text-xs">영소문자/숫자/4~20자</p>
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
          <Label htmlFor="email">이메일</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={set('email')}
            required
          />
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
          <p className="text-muted-foreground text-xs">8자 이상, 공백 없이</p>
        </div>
        <div className="space-y-2">
          <Label htmlFor="phone-area">휴대전화 번호</Label>
          <div className="flex items-center gap-2">
            <Input
              id="phone-area"
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              value={phone.area}
              onChange={setPhonePart('area', 3)}
              placeholder="010"
              className="text-center tabular"
              required
            />
            <span className="text-muted-foreground">-</span>
            <Input
              type="tel"
              inputMode="numeric"
              aria-label="휴대전화 번호 가운데 자리"
              value={phone.middle}
              onChange={setPhonePart('middle', 4)}
              placeholder="1234"
              className="text-center tabular"
              required
            />
            <span className="text-muted-foreground">-</span>
            <Input
              type="tel"
              inputMode="numeric"
              aria-label="휴대전화 번호 마지막 자리"
              value={phone.last}
              onChange={setPhonePart('last', 4)}
              placeholder="5678"
              className="text-center tabular"
              required
            />
          </div>
        </div>
        <label className="flex items-start gap-2.5 text-sm">
          <Checkbox
            checked={agree}
            onCheckedChange={(v) => setAgree(v === true)}
            className="mt-0.5"
          />
          <span className="text-muted-foreground">
            이용약관 및 개인정보처리방침에 동의합니다.
          </span>
        </label>
        <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
          회원가입
        </Button>
      </form>
    </AuthShell>
  )
}
