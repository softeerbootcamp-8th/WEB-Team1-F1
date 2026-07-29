import { FileText } from 'lucide-react'

import { CarThumb } from '@/components/common/car-thumb'
import { Separator } from '@/components/ui/separator'
import { formatMileage } from '@/lib/format'
import type { AuctionCard } from '@/types/domain'

/** 차량 상세 — 썸네일, 스펙 표, 진단 리포트 placeholder. */
export function CarDetail({ auction }: { auction: AuctionCard }) {
  const { car } = auction
  const evaluation = car.evaluation
  const specs: { label: string; value: string }[] = [
    { label: '연식', value: `${car.year}년` },
    { label: '주행거리', value: formatMileage(car.mileageKm) },
    { label: '연료', value: car.fuel },
    { label: '지역', value: car.region },
  ]

  return (
    <div className="space-y-5">
      <div className="bg-muted aspect-[16/10] overflow-hidden rounded-xl border">
        <CarThumb src={auction.thumbnailUrl} alt={car.name} />
      </div>

      <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border bg-border sm:grid-cols-4">
        {specs.map((s) => (
          <div key={s.label} className="bg-card p-4">
            <dt className="text-muted-foreground text-xs">{s.label}</dt>
            <dd className="tabular mt-1 font-semibold">{s.value}</dd>
          </div>
        ))}
      </dl>

      <div className="rounded-xl border p-5">
        <div className="mb-3 flex items-center gap-2">
          <FileText className="text-muted-foreground size-4" />
          <h3 className="text-sm font-semibold">진단 리포트</h3>
        </div>
        <Separator className="mb-4" />
        <ul className="text-muted-foreground grid gap-2 text-sm sm:grid-cols-2">
          <li className="flex justify-between">
            <span>외판·골격</span>
            <span className="text-foreground font-medium">
              {evaluation?.exteriorFrame ?? '진단 예정'}
            </span>
          </li>
          <li className="flex justify-between">
            <span>사고이력</span>
            <span className="text-foreground font-medium">
              {evaluation?.accidentHistory ?? '진단 예정'}
            </span>
          </li>
          <li className="flex justify-between">
            <span>주요 옵션</span>
            <span className="text-foreground font-medium">
              {evaluation?.keyOptions ?? '진단 예정'}
            </span>
          </li>
          <li className="flex justify-between">
            <span>점검 등급</span>
            <span className="text-price-up font-medium">
              {evaluation?.grade ?? '진단 예정'}
            </span>
          </li>
        </ul>
      </div>
    </div>
  )
}
