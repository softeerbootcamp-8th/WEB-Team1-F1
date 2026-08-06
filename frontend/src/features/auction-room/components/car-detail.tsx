import { CarThumb } from '@/components/common/car-thumb'
import { formatMileage } from '@/lib/format'
import type { RoomVehicle } from '@/features/auction-room/types'

interface CarDetailProps {
  vehicle: RoomVehicle
  thumbnailUrl: string | null
}

/** 차량 상세 — 썸네일 + 스펙 표. 평가 진단 데이터는 백엔드가 아직 내려주지 않는다. */
export function CarDetail({ vehicle, thumbnailUrl }: CarDetailProps) {
  const specs: { label: string; value: string }[] = [
    { label: '연식', value: `${vehicle.modelYear}년` },
    { label: '주행거리', value: formatMileage(vehicle.mileage) },
    { label: '연료', value: vehicle.fuelType },
  ]

  return (
    <div className="space-y-5">
      <div className="bg-muted aspect-[16/10] overflow-hidden rounded-xl border">
        <CarThumb src={thumbnailUrl ?? undefined} alt={vehicle.model} />
      </div>

      <dl className="grid grid-cols-3 gap-px overflow-hidden rounded-xl border bg-border">
        {specs.map((s) => (
          <div key={s.label} className="bg-card p-4">
            <dt className="text-muted-foreground text-xs">{s.label}</dt>
            <dd className="tabular mt-1 font-semibold">{s.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
