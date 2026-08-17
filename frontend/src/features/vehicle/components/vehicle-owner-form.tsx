import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'

import { HelpPopover } from '@/components/common/help-popover'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DemoVehicleHelp } from '@/features/vehicle/components/demo-vehicle-help'
import type { VehicleLookupRequest } from '@/features/vehicle/types'
import { cn } from '@/lib/utils'

const PLATE_PATTERN = /^\d{2,3}[가-힣]\d{4}$/

/**
 * 도움말을 버튼 위치에서 아래로 내리는 폭.
 * 이름·번호판 입력 상자와 눈으로 중심이 맞는 자리다 — 정확한 계산이 아니라 눈금이라
 * 입력이 늘거나 표 길이가 바뀌면 다시 본다
 */
const HELP_DROP = 40

export type VehicleOwnerValues = VehicleLookupRequest

interface VehicleOwnerFormProps {
  actionLabel: string
  actionIcon?: LucideIcon
  initialValues?: Partial<VehicleOwnerValues>
  isSubmitting?: boolean
  onSubmit: (values: VehicleOwnerValues) => void
  /** 폼 자체의 폭을 바꿀 때만 넘긴다, 기본은 max-w-sm */
  className?: string
}

export function VehicleOwnerForm({
  actionLabel,
  actionIcon: ActionIcon,
  initialValues,
  isSubmitting = false,
  onSubmit,
  className,
}: VehicleOwnerFormProps) {
  const [ownerName, setOwnerName] = useState(initialValues?.ownerName ?? '')
  const [plateNumber, setPlateNumber] = useState(initialValues?.plateNumber ?? '')

  return (
    <form
      className={cn('mx-auto w-full max-w-sm space-y-7', className)}
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          ownerName: ownerName.trim(),
          plateNumber: plateNumber.trim(),
        })
      }}
    >
      {/* 도움말은 입력 상자 위 오른쪽에 둔다. 폼 안이라 type="button" 이 없으면 눌러서 제출된다 */}
      {/* 폼의 space-y-7 을 그대로 받으면 버튼 높이까지 더해져 이름 라벨이 멀리 밀린다 */}
      <div className="-mb-5 flex justify-end">
        {/* 오른쪽으로 편다. 아래로 열면 표가 입력 상자를 그대로 덮는다.
            폭이 320px 를 넘으면 남는 자리(350px 남짓)에 안 들어가 Radix 가 왼쪽으로 뒤집는다.
            좁은 화면에서는 어차피 뒤집히고, 그때는 아래로 여는 것과 같아진다 */}
        <HelpPopover
          label="넣어 볼 수 있는 데모 차량 보기"
          side="right"
          // 버튼 위쪽을 기준으로 잡고 그만큼 내린다. center 로 두면 버튼(32px) 가운데에
          // 맞춰져 상자 위로 솟고, 그 상태에서는 alignOffset 이 무시된다(Radix 는 start·end 에서만 쓴다)
          align="start"
          alignOffset={HELP_DROP}
          // 좁은 화면에서는 폭을 남는 자리에 맞춘다. Radix 는 뒤집은 뒤에도 트리거와 맞닿는
          // 선까지만 밀어 주므로(limitShift), 넓은 채로 두면 화면 밖으로 20px 쯤 나간다
          contentClassName="w-[min(20rem,calc(100vw-7rem))]"
        >
          <DemoVehicleHelp />
        </HelpPopover>
      </div>
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
