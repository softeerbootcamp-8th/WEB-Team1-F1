import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { emitDealChanged } from './deal-events'
import { useDealList } from './use-deal-list'

const mocks = vi.hoisted(() => ({ fetchDealList: vi.fn() }))

vi.mock('./api', () => ({ fetchDealList: mocks.fetchDealList }))

function page(ids: number[], nextCursor: number | null) {
  return {
    content: ids.map((dealId) => ({ dealId, mySide: 'BUYER' })),
    hasNext: nextCursor != null,
    nextCursor,
  }
}

describe('useDealList 의 거래 변경 신호', () => {
  beforeEach(() => {
    mocks.fetchDealList.mockReset().mockResolvedValue(page([1, 2], null))
  })

  it('신호를 받으면 첫 페이지부터 다시 읽는다', async () => {
    const { result } = renderHook(() => useDealList())

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(mocks.fetchDealList).toHaveBeenCalledTimes(1)

    await act(async () => {
      emitDealChanged()
    })

    await waitFor(() => expect(mocks.fetchDealList).toHaveBeenCalledTimes(2))
  })

  it('보지 않는 목록은 신호를 받지 않는다', async () => {
    renderHook(() => useDealList(false))

    await act(async () => {
      emitDealChanged()
    })

    expect(mocks.fetchDealList).not.toHaveBeenCalled()
  })

  /**
   * 이어 읽기가 날아가는 중에 신호가 오면 세대가 어긋나 그 응답이 버려진다.
   * 그때 "불러오는 중" 표시까지 같이 버려지면 이후 더 보기가 영영 눌리지 않는다.
   */
  it('이어 읽기 도중 신호가 와도 더 보기가 다시 눌린다', async () => {
    let releaseLoadMore: ((value: unknown) => void) | null = null

    mocks.fetchDealList
      .mockResolvedValueOnce(page([1, 2], 2))
      .mockImplementationOnce(
        () => new Promise((resolve) => { releaseLoadMore = resolve }),
      )
      .mockResolvedValue(page([1, 2], 2))

    const { result } = renderHook(() => useDealList())
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => result.current.loadMore())
    await waitFor(() => expect(result.current.isLoadingMore).toBe(true))

    await act(async () => {
      emitDealChanged()
    })
    await waitFor(() => expect(result.current.isLoadingMore).toBe(false))

    // 버려진 이어 읽기가 뒤늦게 끝나도 표시를 되살리지 않는다
    await act(async () => {
      releaseLoadMore?.(page([3], null))
    })

    await waitFor(() => expect(result.current.isLoadingMore).toBe(false))
    const before = mocks.fetchDealList.mock.calls.length
    act(() => result.current.loadMore())

    await waitFor(() => expect(mocks.fetchDealList.mock.calls.length).toBe(before + 1))
  })
})
