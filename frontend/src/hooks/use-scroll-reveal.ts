import { useEffect, type RefObject } from 'react'

const REVEAL_SELECTOR = '[data-reveal]'

/**
 * 범위 안의 data-reveal 요소를 화면 진입 시 한 번만 노출한다.
 * JS가 없거나 모션 축소 설정이 켜진 환경에서는 콘텐츠를 항상 보여 준다.
 */
export function useScrollReveal(scopeRef: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const scope = scopeRef.current

    if (!scope) return

    const targets = Array.from(
      scope.querySelectorAll<HTMLElement>(REVEAL_SELECTOR),
    )
    const reduceMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches

    if (reduceMotion || !('IntersectionObserver' in window)) {
      targets.forEach((target) => target.classList.add('is-revealed'))
      return
    }

    scope.dataset.motionReady = 'true'

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return

          entry.target.classList.add('is-revealed')
          observer.unobserve(entry.target)
        })
      },
      {
        threshold: 0.14,
        rootMargin: '0px 0px -8% 0px',
      },
    )

    const observeTarget = (target: HTMLElement) => {
      if (!target.classList.contains('is-revealed')) {
        observer.observe(target)
      }
    }

    targets.forEach(observeTarget)

    // React Query 응답 뒤 추가되는 경매 카드도 숨은 채 남지 않도록 등록한다.
    const mutationObserver = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (!(node instanceof HTMLElement)) return

          if (node.matches(REVEAL_SELECTOR)) observeTarget(node)
          node.querySelectorAll<HTMLElement>(REVEAL_SELECTOR).forEach(observeTarget)
        })
      })
    })

    mutationObserver.observe(scope, { childList: true, subtree: true })

    return () => {
      mutationObserver.disconnect()
      observer.disconnect()
    }
  }, [scopeRef])
}
