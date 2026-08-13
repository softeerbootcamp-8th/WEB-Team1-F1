import { render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import { Dialog, DialogContent, DialogTitle } from './dialog'

describe('모달 대화상자 스크롤 잠금', () => {
  beforeEach(() => {
    document.body.removeAttribute('data-scroll-locked')
    document.body.removeAttribute('style')
  })

  it('열린 동안 배경 스크롤과 조작을 차단한다', async () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>경매 미리보기</DialogTitle>
        </DialogContent>
      </Dialog>,
    )

    await waitFor(() => {
      expect(document.body.getAttribute('data-scroll-locked')).toBe('1')
      expect(document.body.style.pointerEvents).toBe('none')
    })
  })
})
