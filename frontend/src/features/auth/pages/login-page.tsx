import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthShell } from '../components/auth-shell'
import { useAuth } from '../auth-context'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    // 목업 로그인: 실제로는 백엔드 인증(Filter/Interceptor) 후 세션/JWT 발급.
    // 역할은 계정에 저장된 값이 내려온다(로그인에서 선택하지 않는다).
    login({
      id: 1,
      nickname: '회원',
      role: 'USER',
      email: email || 'demo@race.kr',
    })
    toast.success('로그인되었습니다')
    navigate('/')
  }

  return (
    <AuthShell
      title="로그인"
      subtitle="RACE 계정으로 계속하기"
      footer={
        <>
          아직 회원이 아니신가요?{' '}
          <Link to="/signup" className="text-foreground font-medium underline-offset-4 hover:underline">
            회원가입
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="email">이메일</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">비밀번호</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <Button type="submit" size="lg" className="w-full">
          로그인
        </Button>
      </form>
    </AuthShell>
  )
}
