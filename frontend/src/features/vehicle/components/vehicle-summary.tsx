import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
  TRANSMISSION_LABEL,
} from '@/features/quote/types'
import type { VehicleLookupResponse } from '@/features/vehicle/types'
import { formatMileage } from '@/lib/format'

interface VehicleSummaryProps {
  vehicle: VehicleLookupResponse & { mileage?: number }
}

export function VehicleSummary({ vehicle }: VehicleSummaryProps) {
  return (
    <div>
      {vehicle.mainImageUrl && (
        <img
          src={vehicle.mainImageUrl}
          alt={vehicle.model}
          className="aspect-video w-full rounded-lg object-cover"
        />
      )}
      <div className="mt-3 flex items-baseline justify-between gap-4">
        <p className="text-lg font-semibold">
          {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
        </p>
        {vehicle.mileage !== undefined && (
          <p className="text-muted-foreground tabular shrink-0 text-sm font-medium">
            {formatMileage(vehicle.mileage)}
          </p>
        )}
      </div>
      <p className="text-muted-foreground tabular mt-1 text-sm">
        {vehicle.plateNumber}
      </p>
      <dl className="bg-muted/50 mt-5 grid grid-cols-3 gap-4 rounded-xl p-4 text-sm">
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
