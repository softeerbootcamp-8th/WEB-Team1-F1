import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
  TRANSMISSION_LABEL,
} from '@/features/quote/types'
import type { VehicleLookupResponse } from '@/features/vehicle/types'

interface VehicleSummaryProps {
  vehicle: VehicleLookupResponse
}

export function VehicleSummary({ vehicle }: VehicleSummaryProps) {
  return (
    <div className="bg-muted/50 rounded-xl p-5">
      <p className="text-muted-foreground text-xs">조회된 차량</p>
      {vehicle.mainImageUrl && (
        <img
          src={vehicle.mainImageUrl}
          alt={vehicle.model}
          className="mt-4 aspect-video w-full rounded-lg object-cover"
        />
      )}
      <p className="mt-3 text-lg font-semibold">
        {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
      </p>
      <p className="text-muted-foreground tabular mt-1 text-sm">
        {vehicle.plateNumber}
      </p>
      <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
        <div>
          <dt className="text-muted-foreground">연식</dt>
          <dd className="mt-1 font-medium">{vehicle.modelYear}년</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">연료</dt>
          <dd className="mt-1 font-medium">
            {FUEL_TYPE_LABEL[vehicle.fuelType]}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">변속기</dt>
          <dd className="mt-1 font-medium">
            {TRANSMISSION_LABEL[vehicle.transmission]}
          </dd>
        </div>
      </dl>
    </div>
  )
}
