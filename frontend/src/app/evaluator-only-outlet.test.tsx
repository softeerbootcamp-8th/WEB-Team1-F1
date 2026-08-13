import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { EvaluatorOnlyOutlet } from './evaluator-only-outlet'
import type { UserRole } from '@/types/domain'

function renderRoute(role: UserRole | null) {
  render(
    <MemoryRouter initialEntries={['/evaluations/my']}>
      <Routes>
        <Route element={<EvaluatorOnlyOutlet role={role} />}>
          <Route path="/evaluations/*" element={<div>평가사 업무</div>} />
        </Route>
        <Route path="/" element={<div>홈</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('평가사 전용 경로', () => {
  it('평가사는 평가 업무 화면에 들어간다', () => {
    renderRoute('EVALUATOR')

    expect(screen.getByText('평가사 업무')).toBeTruthy()
  })

  it.each(['GENERAL', 'DEALER'] as const)(
    '%s 회원이 평가 업무 주소를 직접 입력하면 홈으로 보낸다',
    (role) => {
      renderRoute(role)

      expect(screen.getByText('홈')).toBeTruthy()
      expect(screen.queryByText('평가사 업무')).toBeNull()
    },
  )
})
