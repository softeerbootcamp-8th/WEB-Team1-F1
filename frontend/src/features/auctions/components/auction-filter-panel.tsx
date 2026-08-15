import { useState } from 'react'

import { Slider } from '@/components/ui/slider'
import type { AuctionVehicleFilter } from '@/features/auctions/filter'
import { countActiveFilters } from '@/features/auctions/filter'
import type { FuelType, Manufacturer, Transmission } from '@/features/quote/types'
import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
  TRANSMISSION_LABEL,
} from '@/features/quote/types'
import { cn } from '@/lib/utils'
import type { AuctionStatus } from '@/types/domain'

interface AuctionFilterPanelProps {
  value: AuctionVehicleFilter
  onChange: (next: AuctionVehicleFilter) => void
  /** null 이면 상태를 가리지 않는다(=전체) */
  status: AuctionStatus | null
  onStatusChange: (next: AuctionStatus | null) => void
  /** 조건과 상태를 함께 지운다. 둘을 따로 부르면 주소에 한쪽만 반영된다. */
  onReset: () => void
  /** 모바일 패널은 바깥 헤더가 제목과 초기화를 맡는다. */
  showHeader?: boolean
  className?: string
}

// 연도를 박아두면 해가 바뀔 때마다 낡는다. 상한을 실행 시점 기준으로 계산한다.
const CURRENT_YEAR = new Date().getFullYear()

const YEAR_BOUNDS = { min: 2005, max: CURRENT_YEAR, step: 1 }
const MILEAGE_BOUNDS = { min: 0, max: 200_000, step: 10_000 }
const PRICE_BOUNDS = { min: 0, max: 100_000_000, step: 5_000_000 }

const STATUSES: { value: AuctionStatus; label: string }[] = [
  { value: 'LIVE', label: '진행중' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ENDED', label: '종료' },
]

/**
 * 목록 옆에 세워 두는 조건 패널. 조건을 한눈에 두고 여러 개를 연달아 만질 수 있다.
 * 값은 전부 밖에서 받고, 여기서 들고 있는 것은 제조사를 펼쳤는지뿐이다.
 */
export function AuctionFilterPanel({
  value,
  onChange,
  status,
  onStatusChange,
  onReset,
  showHeader = true,
  className,
}: AuctionFilterPanelProps) {
  const brands = Object.entries(MANUFACTURER_LABEL) as [Manufacturer, string][]
  const activeCount = countActiveFilters(value) + (status ? 1 : 0)

  const toggleFuel = (fuel: FuelType) => {
    const next = value.fuelTypes.includes(fuel)
      ? value.fuelTypes.filter((item) => item !== fuel)
      : [...value.fuelTypes, fuel]
    onChange({ ...value, fuelTypes: next })
  }

  return (
    <section aria-label="경매 필터" className={cn('rounded-xl border p-5', className)}>
      {showHeader && (
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">필터</h2>
          {activeCount > 0 && (
            <button
              type="button"
              className="text-muted-foreground hover:text-foreground text-base"
              onClick={onReset}
            >
              초기화
            </button>
          )}
        </div>
      )}

      <Group label="경매 상태">
        <div className="flex flex-wrap gap-2">
          {STATUSES.map((item) => (
            <Chip
              key={item.value}
              selected={status === item.value}
              // 켜진 것을 다시 누르면 전체로 돌아간다. "전체" 칩을 따로 두지 않는 이유다.
              onClick={() => onStatusChange(status === item.value ? null : item.value)}
            >
              {item.label}
            </Chip>
          ))}
        </div>
      </Group>

      <Group label="제조사" divided>
        {/* 스물두 개를 다 세우면 아래 조건들이 화면 밖으로 밀린다. 목록만 따로 굴린다. */}
        <ul className="-mx-1 max-h-56 space-y-0.5 overflow-y-auto px-1">
          {brands.map(([code, label]) => (
            <li key={code}>
              <BrandRow
                selected={value.manufacturer === code}
                onClick={() =>
                  onChange({ ...value, manufacturer: value.manufacturer === code ? null : code })
                }
              >
                {label}
              </BrandRow>
            </li>
          ))}
        </ul>
      </Group>

      <Group label={null} divided>
        <Range
          label="가격"
          bounds={PRICE_BOUNDS}
          min={value.priceMin}
          max={value.priceMax}
          format={(range) =>
            `${formatPrice(range[0])} ~ ${formatPrice(range[1])}${range[1] === PRICE_BOUNDS.max ? '+' : ''}`
          }
          onCommit={(min, max) => onChange({ ...value, priceMin: min, priceMax: max })}
        />
        <Range
          label="주행거리"
          bounds={MILEAGE_BOUNDS}
          min={value.mileageMin}
          max={value.mileageMax}
          format={(range) =>
            `${range[0].toLocaleString()} ~ ${range[1].toLocaleString()}km${range[1] === MILEAGE_BOUNDS.max ? '+' : ''}`
          }
          onCommit={(min, max) => onChange({ ...value, mileageMin: min, mileageMax: max })}
        />
        <Range
          label="연식"
          bounds={YEAR_BOUNDS}
          min={value.modelYearMin}
          max={value.modelYearMax}
          format={(range) => `${range[0]} ~ ${range[1]}년`}
          onCommit={(min, max) => onChange({ ...value, modelYearMin: min, modelYearMax: max })}
        />
      </Group>

      <Group label="연료" divided>
        <div className="flex flex-wrap gap-2">
          {(Object.entries(FUEL_TYPE_LABEL) as [FuelType, string][]).map(([code, label]) => (
            <Chip
              key={code}
              selected={value.fuelTypes.includes(code)}
              onClick={() => toggleFuel(code)}
            >
              {label}
            </Chip>
          ))}
        </div>
      </Group>

      <Group label="변속기" divided>
        <div className="flex flex-wrap gap-2">
          {(Object.entries(TRANSMISSION_LABEL) as [Transmission, string][]).map(
            ([code, label]) => (
              <Chip
                key={code}
                selected={value.transmission === code}
                onClick={() =>
                  onChange({ ...value, transmission: value.transmission === code ? null : code })
                }
              >
                {label}
              </Chip>
            ),
          )}
        </div>
      </Group>
    </section>
  )
}

interface GroupProps {
  label: string | null
  divided?: boolean
  children: React.ReactNode
}

/** 조건 한 묶음. 구분선은 위쪽에 둬서 첫 묶음만 선 없이 제목에 붙는다. */
function Group({ label, divided, children }: GroupProps) {
  return (
    <div className={cn('mt-5', divided && 'mt-5 border-t pt-5')}>
      {label && <p className="mb-3 text-base font-semibold">{label}</p>}
      {children}
    </div>
  )
}

interface ChipProps {
  selected: boolean
  onClick: () => void
  children: React.ReactNode
}

/** 눌러 켜고 끄는 값 하나. 강조는 채도가 아니라 대비로 준다. */
function Chip({ selected, onClick, children }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        'h-10 rounded-full border px-4 text-base whitespace-nowrap transition-colors',
        selected
          ? 'border-foreground bg-foreground text-background font-medium'
          : 'border-input text-foreground hover:border-foreground/60 hover:bg-accent',
      )}
    >
      {children}
    </button>
  )
}

/** 줄 전체가 버튼이고 고른 것은 배경으로 표시한다. 눌러 고르는 것이 하나뿐이라 표시도 하나면 된다. */
function BrandRow({ selected, onClick, children }: ChipProps) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={cn(
        'w-full truncate rounded-lg px-3 py-2.5 text-left text-base transition-colors',
        selected ? 'bg-accent font-semibold' : 'hover:bg-accent/60',
      )}
    >
      {children}
    </button>
  )
}

interface RangeProps {
  label: string
  bounds: { min: number; max: number; step: number }
  min: number | null
  max: number | null
  format: (range: [number, number]) => string
  onCommit: (min: number | null, max: number | null) => void
}

/**
 * 양쪽 핸들 범위 조건. 핸들이 양 끝에 있으면 그쪽은 조건 없음으로 내보낸다.
 * 드래그 중에는 표시만 바꾸고 놓는 순간에만 알린다 — 움직일 때마다 목록을 다시 부르지 않기 위함이다.
 */
function Range({ label, bounds, min, max, format, onCommit }: RangeProps) {
  const [draft, setDraft] = useState<[number, number] | null>(null)

  const applied: [number, number] = [min ?? bounds.min, max ?? bounds.max]
  const range = draft ?? applied

  return (
    <div className="mb-5 last:mb-0">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <span className="text-base font-semibold">{label}</span>
        <span className="text-muted-foreground text-base">{format(range)}</span>
      </div>
      <Slider
        aria-label={label}
        min={bounds.min}
        max={bounds.max}
        step={bounds.step}
        value={range}
        minStepsBetweenThumbs={1}
        onValueChange={(next) => setDraft(next as [number, number])}
        onValueCommit={(next) => {
          setDraft(null)
          const [nextMin, nextMax] = next as [number, number]
          onCommit(nextMin === bounds.min ? null : nextMin, nextMax === bounds.max ? null : nextMax)
        }}
      />
    </div>
  )
}

function formatPrice(value: number): string {
  if (value === 0) return '0'
  if (value >= 100_000_000) return '1억'
  return `${(value / 10_000).toLocaleString()}만`
}
