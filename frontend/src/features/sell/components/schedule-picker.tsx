import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

interface SchedulePickerProps {
  /** 선택 가능한 시각(시 단위). 24는 자정(다음날 00:00)을 의미한다. */
  hours: number[]
  /** 이 시각 이전은 선택할 수 없다. */
  minDateTime: Date
  /** 오늘부터 몇 일치를 스크롤로 보여줄지 */
  daysToShow?: number
  onSelect: (dateTime: Date | null) => void
}

function isSameDate(a: Date, b: Date) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

function combineDateTime(date: Date, hour: number) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour, 0, 0, 0)
}

export function SchedulePicker({
  hours,
  minDateTime,
  daysToShow = 30,
  onSelect,
}: SchedulePickerProps) {
  const today = useMemo(() => {
    const now = new Date()
    now.setHours(0, 0, 0, 0)
    return now
  }, [])

  const days = useMemo(
    () =>
      Array.from({ length: daysToShow }, (_, i) => {
        const date = new Date(today)
        date.setDate(date.getDate() + i)
        return date
      }),
    [today, daysToShow],
  )

  const [selectedDate, setSelectedDate] = useState<Date | null>(null)
  const [selectedHour, setSelectedHour] = useState<number | null>(null)
  const [visibleMonth, setVisibleMonth] = useState(days[0].getMonth())

  const scrollRef = useRef<HTMLDivElement>(null)
  const itemRefs = useRef(new Map<number, HTMLButtonElement>())
  const [canScrollLeft, setCanScrollLeft] = useState(false)
  const [canScrollRight, setCanScrollRight] = useState(false)

  const updateScrollButtons = () => {
    const root = scrollRef.current
    if (!root) return
    setCanScrollLeft(root.scrollLeft > 0)
    setCanScrollRight(root.scrollLeft + root.clientWidth < root.scrollWidth - 1)
  }

  useEffect(() => {
    const root = scrollRef.current
    if (!root) return
    const observer = new IntersectionObserver(
      (entries) => {
        const visibleIndexes = entries
          .filter((entry) => entry.isIntersecting)
          .map((entry) => Number((entry.target as HTMLElement).dataset.index))
        if (visibleIndexes.length > 0) {
          setVisibleMonth(days[Math.min(...visibleIndexes)].getMonth())
        }
      },
      { root, threshold: 0.6 },
    )
    itemRefs.current.forEach((el) => observer.observe(el))
    updateScrollButtons()
    return () => observer.disconnect()
  }, [days])

  const scrollByDays = (direction: 1 | -1) => {
    const root = scrollRef.current
    if (!root) return
    root.scrollBy({ left: direction * root.clientWidth * 0.8, behavior: 'smooth' })
  }

  const isDateDisabled = (date: Date) =>
    !hours.some((hour) => combineDateTime(date, hour) >= minDateTime)

  const isHourDisabled = (hour: number) =>
    !selectedDate || combineDateTime(selectedDate, hour) < minDateTime

  const selectDate = (date: Date) => {
    setSelectedDate(date)
    if (selectedHour !== null) {
      const stillValid = combineDateTime(date, selectedHour) >= minDateTime
      setSelectedHour(stillValid ? selectedHour : null)
      onSelect(stillValid ? combineDateTime(date, selectedHour) : null)
    }
  }

  const selectHour = (hour: number) => {
    if (!selectedDate) return
    setSelectedHour(hour)
    onSelect(combineDateTime(selectedDate, hour))
  }

  return (
    <div className="min-w-0">
      <div className="flex items-center justify-between">
        <p className="text-muted-foreground tabular text-sm font-medium">
          {visibleMonth + 1}월
        </p>
        <div className="flex gap-1">
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="size-7"
            disabled={!canScrollLeft}
            onClick={() => scrollByDays(-1)}
            aria-label="이전 날짜"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="size-7"
            disabled={!canScrollRight}
            onClick={() => scrollByDays(1)}
            aria-label="다음 날짜"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>

      <div
        ref={scrollRef}
        onScroll={updateScrollButtons}
        className="mt-3 flex min-w-0 gap-2 overflow-x-auto pb-2"
      >
        {days.map((date, index) => {
          const disabled = isDateDisabled(date)
          const selected = selectedDate ? isSameDate(date, selectedDate) : false
          const isToday = isSameDate(date, today)
          return (
            <button
              key={date.toISOString()}
              type="button"
              ref={(el) => {
                if (el) itemRefs.current.set(index, el)
                else itemRefs.current.delete(index)
              }}
              data-index={index}
              disabled={disabled}
              onClick={() => selectDate(date)}
              aria-pressed={selected}
              className={cn(
                'flex w-14 shrink-0 flex-col items-center gap-1 rounded-xl border py-2.5 text-sm transition-colors',
                selected
                  ? 'bg-foreground text-background border-foreground'
                  : 'border-border hover:bg-muted',
                disabled && 'cursor-not-allowed opacity-35 hover:bg-transparent',
                isToday && !selected && 'border-primary',
              )}
            >
              <span
                className={cn(
                  'text-xs',
                  selected ? 'text-background/60' : 'text-muted-foreground',
                )}
              >
                {WEEKDAYS[date.getDay()]}
              </span>
              <span className="tabular font-semibold">{date.getDate()}</span>
            </button>
          )
        })}
      </div>

      {selectedDate && (
        <div className="mt-6">
          <p className="text-muted-foreground text-sm">시간 선택</p>
          <div className="mt-3 grid grid-cols-4 gap-2 sm:grid-cols-6">
            {hours.map((hour) => {
              const disabled = isHourDisabled(hour)
              const selected = selectedHour === hour
              return (
                <Button
                  key={hour}
                  type="button"
                  variant={selected ? 'default' : 'outline'}
                  size="sm"
                  disabled={disabled}
                  onClick={() => selectHour(hour)}
                  className="tabular"
                >
                  {String(hour).padStart(2, '0')}:00
                </Button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
