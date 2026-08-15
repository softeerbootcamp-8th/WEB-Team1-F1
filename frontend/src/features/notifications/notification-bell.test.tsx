import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { NotificationBell } from './notification-bell'

vi.mock('@/features/auth/auth-context', () => ({
  useAuth: () => ({ isAuthenticated: true }),
}))

vi.mock('./use-notifications', () => ({
  useNotifications: () => ({
    items: [],
    unreadCount: 0,
    hasNext: false,
    isLoading: false,
    isLoadingMore: false,
    loadMore: vi.fn(),
    markAllRead: vi.fn(),
    markRead: vi.fn(),
  }),
}))

describe('알림함 스크롤 잠금', () => {
  beforeEach(() => {
    document.body.removeAttribute('data-scroll-locked')
    document.body.removeAttribute('style')
  })

  it('열어도 배경 스크롤을 잠그지 않고 Escape로 닫는다', async () => {
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const trigger = screen.getByRole('button', { name: '알림 0건' })
    // Radix 메뉴는 포인터를 누르는 순간 연다. click 만 보내면 실제 브라우저의 입력 순서를
    // 재현하지 못해 메뉴가 열리지 않는다.
    fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false })

    expect(await screen.findByText('새로운 알림이 없습니다.')).toBeTruthy()
    expect(document.body.hasAttribute('data-scroll-locked')).toBe(false)

    fireEvent.keyDown(document, { key: 'Escape' })

    await waitFor(() => expect(trigger.getAttribute('aria-expanded')).toBe('false'))
  })
})
