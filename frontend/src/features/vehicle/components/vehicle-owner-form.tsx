import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { VehicleLookupRequest } from '@/features/vehicle/types'

const PLATE_PATTERN = /^\d{2,3}[가-힣]\d{4}$/

export type VehicleOwnerValues = VehicleLookupRequest

interface VehicleOwnerFormProps {
  actionLabel: string
  actionIcon?: LucideIcon
  initialValues?: Partial<VehicleOwnerValues>
  isSubmitting?: boolean
  onSubmit: (values: VehicleOwnerValues) => void
}

export function VehicleOwnerForm({
  actionLabel,
  actionIcon: ActionIcon,
  initialValues,
  isSubmitting = false,
  onSubmit,
}: VehicleOwnerFormProps) {
  const [ownerName, setOwnerName] = useState(initialValues?.ownerName ?? '')
  const [plateNumber, setPlateNumber] = useState(initialValues?.plateNumber ?? '')

  return (
    <form
      className="mx-auto w-full max-w-sm space-y-7"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          ownerName: ownerName.trim(),
          plateNumber: plateNumber.trim(),
        })
      }}
    >
      <div className="space-y-3">
        <Label htmlFor={`${actionLabel}-plate-number`}>
          차량 번호
        </Label>
        <Input
          id={`${actionLabel}-plate-number`}
          value={plateNumber}
          onChange={(event) => setPlateNumber(event.target.value)}
          placeholder="12가3456"
          className="h-14 px-4 font-semibold"
          autoComplete="off"
          pattern="^\d{2,3}[가-힣]\d{4}$"
          required
        />
      </div>
      <div className="space-y-3">
        <Label htmlFor={`${actionLabel}-owner-name`}>
          소유자명
        </Label>
        <Input
          id={`${actionLabel}-owner-name`}
          value={ownerName}
          onChange={(event) => setOwnerName(event.target.value)}
          placeholder="차량 소유자 이름"
          className="h-14 px-4"
          autoComplete="name"
          maxLength={50}
          required
        />
      </div>
      <Button
        type="submit"
        size="lg"
        className="h-14 w-full"
        disabled={
          isSubmitting ||
          ownerName.trim().length < 1 ||
          ownerName.trim().length > 50 ||
          !PLATE_PATTERN.test(plateNumber.trim())
        }
      >
        {ActionIcon && <ActionIcon className="size-5" />}
        {actionLabel}
      </Button>
    </form>
  )
}
