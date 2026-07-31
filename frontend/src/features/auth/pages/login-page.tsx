import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { getErrorMessage } from '@/lib/axios'
import { AuthShell } from '../components/auth-shell'
import { useAuth } from '../auth-context'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSubmitting(true)
    try {
      await login({ username, password })
      toast.success('로그인되었습니다')
      navigate('/')
    } catch (error) {
      toast.error(getErrorMessage(error, '로그인에 실패했습니다'))
    } finally {
      setIsSubmitting(false)
    }
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
          <Label htmlFor="username">아이디</Label>
          <Input
            id="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">비밀번호</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
          로그인
        </Button>
      </form>
    </AuthShell>
  )
}
