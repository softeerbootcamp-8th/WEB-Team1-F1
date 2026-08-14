import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Header } from './header'

const mocks = vi.hoisted(() => ({
  useAuth: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/features/auth/auth-context', () => ({
  ROLE_LABEL: { GENERAL: '개인', DEALER: '딜러', EVALUATOR: '평가사' },
  useAuth: mocks.useAuth,
}))

vi.mock('@/features/notifications/notification-bell', () => ({
  NotificationBell: () => null,
}))

describe('Header 모바일 메뉴', () => {
  beforeEach(() => {
    mocks.logout.mockReset().mockResolvedValue(undefined)
    mocks.useAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      logout: mocks.logout,
    })
  })

  it('숨겨진 주요 메뉴를 열고 현재 위치를 표시한 뒤 실행 버튼으로 초점을 돌려준다', async () => {
    render(
      <MemoryRouter initialEntries={['/auctions']}>
        <Header />
      </MemoryRouter>,
    )

    const trigger = screen.getByRole('button', { name: '주요 메뉴 열기' })
    trigger.focus()
    fireEvent.click(trigger)

    const dialog = screen.getByRole('dialog', { name: '메뉴' })
    const navigation = within(dialog).getByRole('navigation', { name: '모바일 주요 메뉴' })
    expect(within(navigation).getAllByRole('link')).toHaveLength(5)
    const current = within(navigation).getByRole('link', { name: '경매 목록' })
    expect(current.getAttribute('aria-current')).toBe('page')
    expect(current.className).toContain('block')
    expect(current.className).toContain('bg-accent')
    expect(within(dialog).getByRole('link', { name: '로그인' }).getAttribute('href')).toBe(
      '/login',
    )

    fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '메뉴' })).toBeNull())
    expect(document.activeElement).toBe(trigger)
  })
})
