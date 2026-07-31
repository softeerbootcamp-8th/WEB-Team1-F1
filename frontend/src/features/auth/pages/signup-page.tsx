import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { AuthShell } from '../components/auth-shell'
import { RoleSelect } from '../components/role-select'
import { useAuth } from '../auth-context'
import type { UserRole } from '@/types/domain'

export function SignupPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ nickname: '', email: '', password: '' })
  const [role, setRole] = useState<UserRole>('USER')
  const [agree, setAgree] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!agree) {
      toast.error('약관에 동의해 주세요')
      return
    }
    login({
      id: Math.floor(Math.random() * 100000),
      nickname: form.nickname || '신규회원',
      role,
      email: form.email || 'new@race.kr',
    })
    toast.success('회원가입이 완료되었습니다')
    navigate('/')
  }

  return (
    <AuthShell
      title="회원가입"
      subtitle="30초 만에 시작하기"
      footer={
        <>
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="text-foreground font-medium underline-offset-4 hover:underline">
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
        <div className="space-y-2">
          <Label htmlFor="nickname">닉네임</Label>
          <Input id="nickname" value={form.nickname} onChange={set('nickname')} placeholder="닉네임" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">이메일</Label>
          <Input id="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} placeholder="you@example.com" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">비밀번호</Label>
          <Input id="password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')} placeholder="8자 이상" />
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
        <Button type="submit" size="lg" className="w-full">
          회원가입
        </Button>
      </form>
    </AuthShell>
  )
}
