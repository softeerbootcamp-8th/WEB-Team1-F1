import { FileText } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { CarThumb } from '@/components/common/car-thumb'
import { formatMileage } from '@/lib/format'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL } from '@/features/quote/types'
import type { RoomVehicle } from '@/features/auction-room/types'

interface CarDetailProps {
  vehicle: RoomVehicle
}

/** 차량 상세 — 썸네일 + 스펙 표 + 진단서 링크. 방의 모든 단계가 이 블록을 같은 자리에 쓴다. */
export function CarDetail({ vehicle }: CarDetailProps) {
  const specs: { label: string; value: string }[] = [
    { label: '제조사', value: MANUFACTURER_LABEL[vehicle.manufacturer] },
    { label: '연식', value: `${vehicle.modelYear}년` },
    { label: '주행거리', value: formatMileage(vehicle.mileage) },
    { label: '연료', value: FUEL_TYPE_LABEL[vehicle.fuelType] },
  ]

  return (
    <div className="space-y-5">
      <div className="bg-muted aspect-[16/10] overflow-hidden rounded-xl border">
        <CarThumb src={vehicle.thumbnailUrl ?? undefined} alt={vehicle.model} />
      </div>

      <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border bg-border sm:grid-cols-4">
        {specs.map((s) => (
          <div key={s.label} className="bg-card p-4">
            <dt className="text-muted-foreground text-xs">{s.label}</dt>
            <dd className="tabular mt-1 font-semibold">{s.value}</dd>
          </div>
        ))}
      </dl>

      {/* 새 탭으로 연다, 방 위에 띄우면 읽는 동안 현재가와 남은 시간이 가려진다 */}
      <Button asChild variant="outline" className="w-full">
        <a href={vehicle.diagnosticReportUrl} target="_blank" rel="noreferrer">
          <FileText />
          진단서 PDF 보기
        </a>
      </Button>
    </div>
  )
}
