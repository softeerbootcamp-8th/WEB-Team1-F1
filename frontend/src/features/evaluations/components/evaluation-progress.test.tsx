import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { EvaluationProgress } from './evaluation-progress'

describe('방문견적 진행 단계', () => {
  it.each([
    ['REQUESTED', false, '배정 대기'],
    ['REQUESTED', true, '평가 진행 중'],
    ['APPROVED', true, '차량 진단 완료'],
  ] as const)('%s·배정 %s의 현재 단계를 %s로 표시한다', (status, assigned, current) => {
    render(<EvaluationProgress status={status} assigned={assigned} />)

    expect(screen.getByText(current).getAttribute('aria-current')).toBe('step')
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    expect(screen.getByRole('list').className).toContain('grid-cols-3')
    expect(screen.getAllByText(/배정 대기|평가 진행 중|차량 진단 완료/)).toHaveLength(3)
  })

  it('차량 진단 완료 단계는 진행 중 이중 원 대신 체크로 표시한다', () => {
    render(<EvaluationProgress status="APPROVED" assigned />)

    const completedStep = screen.getByText('차량 진단 완료').closest('li')
    const marker = completedStep?.querySelector('[data-slot="evaluation-step-marker"]')

    expect(marker?.className).toContain('bg-primary')
    expect(marker?.className).not.toContain('after:size-2')
    expect(marker?.querySelector('svg')).toBeTruthy()
  })
})
