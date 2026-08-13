import { useQuery } from '@tanstack/react-query'
import { LoaderCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { fetchDemoVehicles } from '@/features/vehicle/api'
import { MANUFACTURER_LABEL } from '@/features/quote/types'

/**
 * 도움말 안에 뜨는 데모 차량 표.
 *
 * 이 컴포넌트는 도움말이 열려 있을 때만 마운트되므로(HelpPopover 주석 참고) 첫 요청이 곧
 * "처음 열 때"다. 두 번째부터는 staleTime 안에서 캐시를 쓰고, 실패하면 다시 열 때 재시도한다.
 *
 * 로딩·실패·빈 목록을 각각 다르게 말한다. 셋을 "표가 비어 있음"으로 뭉치면 사용자가 기다려야
 * 하는지 다시 열어야 하는지 판단할 수 없다.
 */
export function DemoVehicleHelp() {
  const { data, isPending, isError, refetch, isFetching } = useQuery({
    queryKey: ['demo-vehicles'],
    queryFn: fetchDemoVehicles,
    // 진행 중 여부가 초 단위로 바뀌는 값이 아니라 열 때마다 다시 받을 이유가 없다
    staleTime: 60_000,
  })

  return (
    <div className="space-y-3">
      <div>
        <p className="text-sm font-semibold">넣어 볼 수 있는 차량</p>
        <p className="text-muted-foreground mt-1 text-xs">
          아래 이름과 차량 번호를 그대로 입력하면 조회됩니다.
        </p>
      </div>

      {isPending && (
        <p
          className="text-muted-foreground flex items-center gap-2 py-4 text-xs"
          role="status"
        >
          <LoaderCircle className="size-4 animate-spin" aria-hidden />
          차량 목록을 불러오는 중입니다
        </p>
      )}

      {isError && (
        <div className="space-y-2 py-2" role="status">
          <p className="text-xs">차량 목록을 불러오지 못했습니다.</p>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={isFetching}
            onClick={() => void refetch()}
          >
            다시 시도
          </Button>
        </div>
      )}

      {data && data.length === 0 && (
        <p className="text-muted-foreground py-4 text-xs" role="status">
          지금은 사용할 수 있는 데모 차량이 없습니다.
        </p>
      )}

      {data && data.length > 0 && (
        <table className="w-full text-left text-xs">
          <caption className="sr-only">
            사용할 수 있는 데모 차량 {data.length}대
          </caption>
          <thead className="text-muted-foreground">
            <tr>
              <th scope="col" className="py-1 font-medium">
                차량 번호
              </th>
              <th scope="col" className="py-1 font-medium">
                이름
              </th>
              <th scope="col" className="py-1 font-medium">
                차량
              </th>
            </tr>
          </thead>
          <tbody>
            {data.map((vehicle) => (
              <tr key={vehicle.plateNumber} className="border-t">
                <td className="py-1.5 font-semibold whitespace-nowrap tabular-nums">
                  {vehicle.plateNumber}
                </td>
                <td className="py-1.5 whitespace-nowrap">{vehicle.ownerName}</td>
                <td className="py-1.5">
                  {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
                  <span className="text-muted-foreground">
                    {' '}
                    {vehicle.modelYear}년
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
