import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'

import { HelpPopover } from '@/components/common/help-popover'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DemoVehicleHelp } from '@/features/vehicle/components/demo-vehicle-help'
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
      className="space-y-7"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          ownerName: ownerName.trim(),
          plateNumber: plateNumber.trim(),
        })
      }}
    >
      {/* 도움말은 입력 상자 위 오른쪽에 둔다. 폼 안이라 type="button" 이 없으면 눌러서 제출된다 */}
      <div className="flex justify-end">
        {/* 옆으로 빼 봤지만 카드가 화면 오른쪽에 붙어 있어 Radix 가 다시 왼쪽으로 뒤집는다.
            아래로 열어 입력 상자를 잠깐 덮는 편이 낫다 — 벗어나면 닫히는 도움말이다 */}
        <HelpPopover
          label="넣어 볼 수 있는 데모 차량 보기"
          contentClassName="w-90"
        >
          <DemoVehicleHelp />
        </HelpPopover>
      </div>
      <div className="space-y-3">
        <Label className="text-lg" htmlFor={`${actionLabel}-owner-name`}>
          이름
        </Label>
        <Input
          id={`${actionLabel}-owner-name`}
          value={ownerName}
          onChange={(event) => setOwnerName(event.target.value)}
          placeholder="차량 소유자 이름"
          className="h-14 px-4 text-lg md:text-lg"
          autoComplete="name"
          maxLength={50}
          required
        />
      </div>
      <div className="space-y-3">
        <Label className="text-lg" htmlFor={`${actionLabel}-plate-number`}>
          차량 번호판
        </Label>
        <Input
          id={`${actionLabel}-plate-number`}
          value={plateNumber}
          onChange={(event) => setPlateNumber(event.target.value)}
          placeholder="12가3456"
          className="h-14 px-4 text-lg font-semibold md:text-lg"
          autoComplete="off"
          pattern="^\d{2,3}[가-힣]\d{4}$"
          required
        />
      </div>
      <Button
        type="submit"
        size="lg"
        className="h-14 w-full text-lg"
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
