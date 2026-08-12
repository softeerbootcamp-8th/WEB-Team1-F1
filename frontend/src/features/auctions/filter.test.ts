import { describe, expect, it } from 'vitest'

import { similarFilter, toFilterParams } from './filter'

describe('similarFilter', () => {
  it('제조사는 그대로 걸고 다른 조건은 걸지 않는다', () => {
    const filter = similarFilter('HYUNDAI', 12_300_000)

    expect(filter.manufacturer).toBe('HYUNDAI')
    expect(filter.fuelTypes).toEqual([])
    expect(filter.transmission).toBeNull()
    expect(filter.mileageMin).toBeNull()
    expect(filter.modelYearMin).toBeNull()
  })

  it('값 범위는 기준값의 위아래 20%다', () => {
    const filter = similarFilter('HYUNDAI', 12_300_000)

    expect(filter.priceMin).toBe(9_840_000)
    expect(filter.priceMax).toBe(14_760_000)
  })

  it('끊는 방향이 바깥이라 범위가 좁아지지 않는다', () => {
    // 위아래 20%가 1,003,200원과 1,504,800원이라 만원 단위로 딱 떨어지지 않는다
    const filter = similarFilter('KIA', 1_254_000)

    expect(filter.priceMin).toBe(1_000_000)
    expect(filter.priceMax).toBe(1_510_000)
  })

  it('주소 쿼리로 나갈 때 목록 조회와 같은 이름을 쓴다', () => {
    const query = toFilterParams(similarFilter('KIA', 20_000_000)).toString()

    expect(query).toBe('manufacturer=KIA&priceMin=16000000&priceMax=24000000')
  })
})
