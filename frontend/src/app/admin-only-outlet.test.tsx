import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AdminOnlyOutlet } from './admin-only-outlet'
import type { UserRole } from '@/types/domain'

function renderRoute(role: UserRole | null) {
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route element={<AdminOnlyOutlet role={role} />}>
          <Route path="/admin/*" element={<div>관리자 홈</div>} />
        </Route>
        <Route path="/" element={<div>홈</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('관리자 전용 경로', () => {
  it('관리자는 운영 화면에 들어간다', () => {
    renderRoute('ADMIN')

    expect(screen.getByText('관리자 홈')).toBeTruthy()
  })

  it.each(['GENERAL', 'DEALER', 'EVALUATOR'] as const)(
    '%s 회원이 관리자 주소를 직접 입력하면 홈으로 보낸다',
    (role) => {
      renderRoute(role)

      expect(screen.getByText('홈')).toBeTruthy()
      expect(screen.queryByText('관리자 홈')).toBeNull()
    },
  )

  it('비회원도 홈으로 보낸다', () => {
    renderRoute(null)

    expect(screen.getByText('홈')).toBeTruthy()
  })
})
