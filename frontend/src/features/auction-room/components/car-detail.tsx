import { memo, useEffect, useState } from 'react'
import { FileText } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Carousel,
  CarouselContent,
  CarouselItem,
  CarouselNext,
  CarouselPrevious,
  type CarouselApi,
} from '@/components/ui/carousel'
import { CarThumb } from '@/components/common/car-thumb'
import { cn } from '@/lib/utils'
import { formatMileage } from '@/lib/format'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL } from '@/features/quote/types'
import type { RoomVehicle } from '@/features/auction-room/types'

interface CarDetailProps {
  vehicle: RoomVehicle
}

/** 차량 상세 — 사진 캐러셀 + 스펙 표 + 진단서 링크. 방의 모든 단계가 이 블록을 같은 자리에 쓴다. */
export function CarDetail({ vehicle }: CarDetailProps) {
  const specs: { label: string; value: string }[] = [
    { label: '제조사', value: MANUFACTURER_LABEL[vehicle.manufacturer] },
    { label: '연식', value: `${vehicle.modelYear}년` },
    { label: '주행거리', value: formatMileage(vehicle.mileage) },
    { label: '연료', value: FUEL_TYPE_LABEL[vehicle.fuelType] },
  ]

  return (
    <div className="space-y-5">
      <CarPhotos model={vehicle.model} imageUrls={vehicle.imageUrls} />

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

/**
 * 평가사가 올린 순서 그대로 넘겨 본다, 첫 장이 대표다.
 * 한 장뿐이면 넘길 것이 없으므로 화살표와 점을 그리지 않는다.
 */
const CarPhotos = memo(function CarPhotos({
  model,
  imageUrls,
}: {
  model: string
  imageUrls: string[]
}) {
  const [api, setApi] = useState<CarouselApi>()
  const [current, setCurrent] = useState(0)

  useEffect(() => {
    if (!api) return

    const sync = () => setCurrent(api.selectedScrollSnap())
    sync()
    api.on('select', sync)

    return () => {
      api.off('select', sync)
    }
  }, [api])

  const hasMany = imageUrls.length > 1

  return (
    <div className="space-y-3">
      {/* 끝에서 처음으로 돌아온다, 장수가 적어 한 바퀴가 금방이다 */}
      <Carousel setApi={setApi} opts={{ loop: hasMany }} className="w-full">
        <CarouselContent>
          {imageUrls.map((url, index) => (
            <CarouselItem key={url}>
              <div className="bg-muted aspect-[16/10] overflow-hidden rounded-xl border">
                <CarThumb src={url} alt={`${model} 사진 ${index + 1}`} loading="eager" />
              </div>
            </CarouselItem>
          ))}
        </CarouselContent>

        {hasMany && (
          <>
            <CarouselPrevious className="left-3" />
            <CarouselNext className="right-3" />
          </>
        )}
      </Carousel>

      {hasMany && (
        <div className="flex justify-center gap-1.5">
          {imageUrls.map((url, index) => (
            <span
              key={url}
              className={cn(
                'size-1.5 rounded-full transition-colors',
                index === current ? 'bg-foreground' : 'bg-muted-foreground/30',
              )}
              aria-hidden
            />
          ))}
        </div>
      )}
    </div>
  )
})
