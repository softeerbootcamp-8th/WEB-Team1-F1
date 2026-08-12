import type { LucideIcon } from 'lucide-react'
import { useLayoutEffect, useRef, useState } from 'react'

import { HelpPopover } from '@/components/common/help-popover'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DemoVehicleHelp } from '@/features/vehicle/components/demo-vehicle-help'
import type { VehicleLookupRequest } from '@/features/vehicle/types'

const PLATE_PATTERN = /^\d{2,3}[가-힣]\d{4}$/

/** 도움말 버튼 높이(size-8). 도움말을 상자와 상하 대칭으로 놓을 때 기준이 된다 */
const HELP_BUTTON_SIZE = 32

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

  // 도움말을 상자와 상하 대칭으로 놓기 위한 내림폭.
  // 도움말은 버튼을 기준으로 열려 상자 위쪽에 치우치므로, 상자 높이의 절반만큼(버튼 높이는 빼고)
  // 내려야 가운데가 맞는다. 값을 상수로 박으면 입력이 하나 늘 때 조용히 어긋난다
  const formRef = useRef<HTMLFormElement>(null)
  const [helpOffset, setHelpOffset] = useState(0)

  useLayoutEffect(() => {
    const form = formRef.current
    if (!form) return

    const measure = () =>
      setHelpOffset((form.getBoundingClientRect().height - HELP_BUTTON_SIZE) / 2)

    measure()

    // 검증 문구가 붙거나 화면이 좁아지면 높이가 바뀐다
    const observer = new ResizeObserver(measure)
    observer.observe(form)

    return () => observer.disconnect()
  }, [])

  return (
    <form
      ref={formRef}
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
      {/* 폼의 space-y-7 을 그대로 받으면 버튼 높이까지 더해져 이름 라벨이 멀리 밀린다 */}
      <div className="-mb-5 flex justify-end">
        {/* 오른쪽으로 편다. 아래로 열면 표가 입력 상자를 그대로 덮는다.
            폭이 320px 를 넘으면 남는 자리(350px 남짓)에 안 들어가 Radix 가 왼쪽으로 뒤집는다.
            좁은 화면에서는 어차피 뒤집히고, 그때는 아래로 여는 것과 같아진다 */}
        <HelpPopover
          label="넣어 볼 수 있는 데모 차량 보기"
          side="right"
          align="center"
          alignOffset={helpOffset}
          // 좁은 화면에서는 폭을 남는 자리에 맞춘다. Radix 는 뒤집은 뒤에도 트리거와 맞닿는
          // 선까지만 밀어 주므로(limitShift), 넓은 채로 두면 화면 밖으로 20px 쯤 나간다
          contentClassName="w-[min(20rem,calc(100vw-7rem))]"
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
