import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export interface VehicleOwnerValues {
  ownerName: string
  plateNumber: string
}

interface VehicleOwnerFormProps {
  actionLabel: string
  actionIcon: LucideIcon
  onSubmit: (values: VehicleOwnerValues) => void
}

export function VehicleOwnerForm({
  actionLabel,
  actionIcon: ActionIcon,
  onSubmit,
}: VehicleOwnerFormProps) {
  const [ownerName, setOwnerName] = useState('')
  const [plateNumber, setPlateNumber] = useState('')

  return (
    <form
      className="space-y-5"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({ ownerName: ownerName.trim(), plateNumber: plateNumber.trim() })
      }}
    >
      <div className="space-y-2">
        <Label htmlFor={`${actionLabel}-owner-name`}>이름</Label>
        <Input
          id={`${actionLabel}-owner-name`}
          value={ownerName}
          onChange={(event) => setOwnerName(event.target.value)}
          placeholder="차량 소유자 이름"
          autoComplete="name"
          required
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor={`${actionLabel}-plate-number`}>차량 번호판</Label>
        <Input
          id={`${actionLabel}-plate-number`}
          value={plateNumber}
          onChange={(event) => setPlateNumber(event.target.value)}
          placeholder="12가3456"
          className="h-14 text-lg font-semibold"
          autoComplete="off"
          required
        />
      </div>
      <Button
        type="submit"
        size="lg"
        className="w-full"
        disabled={ownerName.trim().length < 2 || plateNumber.trim().length < 7}
      >
        <ActionIcon className="size-4" />
        {actionLabel}
      </Button>
    </form>
  )
}
