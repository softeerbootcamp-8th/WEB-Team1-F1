import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { EvaluatorRestrictedOutlet } from './evaluator-restricted-outlet'
import type { UserRole } from '@/types/domain'

function renderRoute(role: UserRole | null, path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<EvaluatorRestrictedOutlet role={role} />}>
          <Route path="/mypage/*" element={<div>마이페이지</div>} />
          <Route path="/sell/*" element={<div>판매 화면</div>} />
          <Route path="/quote/*" element={<div>시세 화면</div>} />
          <Route path="/deals/*" element={<div>거래 화면</div>} />
        </Route>
        <Route path="/" element={<div>평가사 홈</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('평가사 제한 경로', () => {
  it.each([
    '/mypage/deals/1',
    '/mypage/sales',
    '/mypage/purchases',
    '/sell',
    '/sell/auction-post',
    '/quote/result',
    '/deals/1',
  ])(
    '평가사가 %s 주소를 직접 입력해도 홈으로 보낸다',
    (path) => {
      renderRoute('EVALUATOR', path)

      expect(screen.getByText('평가사 홈')).toBeTruthy()
    },
  )

  it.each(['GENERAL', 'DEALER'] as const)(
    '%s 회원은 마이페이지에 들어갈 수 있다',
    (role) => {
      renderRoute(role, '/mypage/deals/1')

      expect(screen.getByText('마이페이지')).toBeTruthy()
    },
  )
})
