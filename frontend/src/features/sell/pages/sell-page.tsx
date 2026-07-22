import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, Search, Gavel, ClipboardCheck } from 'lucide-react'
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
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'
import { formatKRW } from '@/lib/format'

type Step = 0 | 1 | 2

const STEPS = [
  { title: '시세 조회', icon: Search },
  { title: '평가사 방문 신청', icon: ClipboardCheck },
  { title: '게시글 작성', icon: Gavel },
]

/** 로컬 시각 → datetime-local input 값 (현재+1h) */
function defaultStartAt() {
  const d = new Date(Date.now() + 60 * 60_000)
  d.setSeconds(0, 0)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function SellPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>(0)
  const [car, setCar] = useState({ model: '', year: '', mileage: '' })
  const [quote, setQuote] = useState<number | null>(null)
  const [startPrice, setStartPrice] = useState('')
  const [startAt, setStartAt] = useState(defaultStartAt)

  const runQuote = () => {
    // 목업 시세: 실제로는 시세 API 조회
    const base = 25_000_000 - Number(car.mileage || 0) * 100
    const estimated = Math.max(5_000_000, Math.round(base / 100_000) * 100_000)
    setQuote(estimated)
    setStartPrice(String(estimated))
  }

  return (
    <main aria-label="내 차 팔기" className="mx-auto max-w-3xl px-6 py-10">
      <header className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
          내 차 팔기
        </h1>
        <p className="text-muted-foreground mt-2 text-sm">
          시세 조회 후 평가사 방문을 신청하고, 승인되면 경매를 등록하세요.
        </p>
      </header>

      {/* Stepper */}
      <ol className="mb-8 flex items-center">
        {STEPS.map((s, i) => {
          const done = i < step
          const active = i === step
          const Icon = done ? Check : s.icon
          return (
            <li key={s.title} className="flex flex-1 items-center last:flex-none">
              <div className="flex items-center gap-2.5">
                <span
                  className={cn(
                    'flex size-9 shrink-0 items-center justify-center rounded-full border text-sm transition-colors',
                    done && 'bg-primary text-primary-foreground border-primary',
                    active && 'border-primary text-primary ring-primary/20 ring-2',
                    !done && !active && 'text-muted-foreground',
                  )}
                >
                  <Icon className="size-4" />
                </span>
                <span
                  className={cn(
                    'hidden text-sm font-medium sm:block',
                    active ? 'text-foreground' : 'text-muted-foreground',
                  )}
                >
                  {s.title}
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <span
                  className={cn(
                    'mx-3 h-px flex-1',
                    done ? 'bg-primary' : 'bg-border',
                  )}
                />
              )}
            </li>
          )
        })}
      </ol>

      <div className="rounded-xl border p-6">
        {/* STEP 0 — 시세 조회 */}
        {step === 0 && (
          <div className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="model">차량 모델</Label>
                <Input
                  id="model"
                  placeholder="예) 더 뉴 K5 2.0 가솔린"
                  value={car.model}
                  onChange={(e) => setCar({ ...car, model: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="year">연식</Label>
                <Select value={car.year} onValueChange={(v) => setCar({ ...car, year: v })}>
                  <SelectTrigger id="year" className="w-full">
                    <SelectValue placeholder="연식 선택" />
                  </SelectTrigger>
                  <SelectContent>
                    {[2024, 2023, 2022, 2021, 2020, 2019].map((y) => (
                      <SelectItem key={y} value={String(y)}>
                        {y}년
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="mileage">주행거리 (km)</Label>
                <Input
                  id="mileage"
                  type="number"
                  inputMode="numeric"
                  placeholder="45000"
                  value={car.mileage}
                  onChange={(e) => setCar({ ...car, mileage: e.target.value })}
                  className="tabular"
                />
              </div>
            </div>

            {quote !== null && (
              <div className="bg-accent flex items-center justify-between rounded-lg p-4" role="status">
                <span className="text-sm">예상 시세</span>
                <span className="tabular text-xl font-semibold">
                  {formatKRW(quote)}
                </span>
              </div>
            )}

            <Separator />
            <div className="flex justify-between gap-3">
              <Button variant="outline" onClick={runQuote} disabled={!car.model || !car.year}>
                <Search className="size-4" />
                시세 조회
              </Button>
              <Button onClick={() => setStep(1)} disabled={quote === null}>
                다음
              </Button>
            </div>
          </div>
        )}

        {/* STEP 1 — 평가사 방문 신청 */}
        {step === 1 && (
          <div className="space-y-5">
            <p className="text-muted-foreground text-sm">
              평가사가 차량을 방문 진단합니다. 희망 지역과 연락처를 입력해 주세요.
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="region">희망 지역</Label>
                <Input id="region" placeholder="서울 강서구" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="phone">연락처</Label>
                <Input id="phone" type="tel" placeholder="010-0000-0000" />
              </div>
            </div>
            <Separator />
            <div className="flex justify-between gap-3">
              <Button variant="outline" onClick={() => setStep(0)}>
                이전
              </Button>
              <Button
                onClick={() => {
                  toast.success('평가사 방문 신청이 접수되었습니다', {
                    description: '승인 알림을 받으면 게시글을 작성할 수 있어요.',
                  })
                  setStep(2)
                }}
              >
                방문 신청
              </Button>
            </div>
          </div>
        )}

        {/* STEP 2 — 게시글 작성 (승인됨 가정) */}
        {step === 2 && (
          <div className="space-y-5">
            <div className="bg-success/10 text-success flex items-center gap-2 rounded-lg px-3 py-2 text-sm" role="status">
              <Check className="size-4" />
              평가가 승인되었습니다. 경매 정보를 입력해 등록하세요.
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="start-price">시작가 (원)</Label>
                <Input
                  id="start-price"
                  type="number"
                  inputMode="numeric"
                  value={startPrice}
                  onChange={(e) => setStartPrice(e.target.value)}
                  className="tabular"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="start-at">시작 시각</Label>
                <Input
                  id="start-at"
                  type="datetime-local"
                  min={defaultStartAt()}
                  value={startAt}
                  onChange={(e) => setStartAt(e.target.value)}
                  className="tabular"
                />
                <p className="text-muted-foreground text-xs">
                  현재 시각 기준 1시간 이후부터 지정할 수 있습니다.
                </p>
              </div>
            </div>
            <Separator />
            <div className="flex justify-between gap-3">
              <Button variant="outline" onClick={() => setStep(1)}>
                이전
              </Button>
              <Button
                onClick={() => {
                  toast.success('경매가 등록되었습니다')
                  navigate('/')
                }}
              >
                <Gavel className="size-4" />
                경매 등록
              </Button>
            </div>
          </div>
        )}
      </div>
    </main>
  )
}
