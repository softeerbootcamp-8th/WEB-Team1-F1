import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { HelpPopover } from './help-popover'

function renderHelp() {
  return render(
    <HelpPopover label="도움말 열기">
      <p>도움말 내용</p>
    </HelpPopover>,
  )
}

const trigger = () => screen.getByRole('button', { name: '도움말 열기' })
const isOpen = () => trigger().getAttribute('aria-expanded') === 'true'

/** 닫기 유예(120ms)를 넘긴다 */
function runCloseDelay() {
  act(() => {
    vi.advanceTimersByTime(200)
  })
}

describe('도움말 버튼', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('마우스를 올리면 열리고 벗어나면 닫힌다', () => {
    renderHelp()

    fireEvent.pointerEnter(trigger(), { pointerType: 'mouse' })
    expect(isOpen()).toBe(true)

    fireEvent.pointerLeave(trigger(), { pointerType: 'mouse' })
    // 유예 안에서는 아직 열려 있다 — 콘텐츠로 마우스를 옮기는 사이 닫히면 표를 읽을 수 없다
    expect(isOpen()).toBe(true)

    runCloseDelay()
    expect(isOpen()).toBe(false)
  })

  it('터치로 올라온 포인터는 hover 로 열지 않는다', () => {
    // 터치에는 hover 가 없다. 눌러서 여는 경로가 따로 있어 여기서 열면 탭 한 번에 열리고 닫힌다
    renderHelp()

    fireEvent.pointerEnter(trigger(), { pointerType: 'touch' })

    expect(isOpen()).toBe(false)
  })

  it('Esc 로 닫은 뒤에는 커서가 버튼에 남아 있어도 다시 열리지 않는다', () => {
    // 닫은 것이 사용자의 의사표시다. 곧바로 되열리면 Esc 가 듣지 않는 것으로 보인다
    renderHelp()

    fireEvent.pointerEnter(trigger(), { pointerType: 'mouse' })
    expect(isOpen()).toBe(true)

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(isOpen()).toBe(false)

    fireEvent.pointerEnter(trigger(), { pointerType: 'mouse' })
    expect(isOpen()).toBe(false)

    // 커서를 치우면 다시 열 수 있다
    fireEvent.pointerLeave(trigger(), { pointerType: 'mouse' })
    runCloseDelay()
    fireEvent.pointerEnter(trigger(), { pointerType: 'mouse' })

    expect(isOpen()).toBe(true)
  })

  it('열려 있을 때만 내용을 그린다', () => {
    // 안에서 데이터를 받아 오는 도움말이 "열 때 요청"이 되는 것이 이 성질에 기대고 있다
    renderHelp()

    expect(screen.queryByText('도움말 내용')).toBeNull()

    fireEvent.pointerEnter(trigger(), { pointerType: 'mouse' })

    expect(screen.getByText('도움말 내용')).toBeTruthy()
  })
})
