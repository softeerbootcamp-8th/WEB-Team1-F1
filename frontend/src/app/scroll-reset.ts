import { useLayoutEffect } from 'react'
import { useLocation, useNavigationType } from 'react-router-dom'

/**
 * 이 주소로 들어가는 이동에서는 맨 위로 올리지 않는다는 표식. 주소를 함께 담는 이유는,
 * 이동 없이 표식만 세워진 경우에 다음 이동이 엉뚱하게 면제받는 것을 막기 위해서다.
 */
let keepScrollFor: string | null = null

/**
 * 보던 높이를 스스로 되살리는 화면이 마운트하는 렌더에서 부른다.
 *
 * 표식을 이동을 만드는 링크가 아니라 되살리는 화면이 세우는 이유: 캐시가 살아 있어 되살릴 수
 * 있는지를 아는 것은 그 화면뿐이다. 링크 쪽에 두면 같은 목록으로 가는 링크가 늘 때마다
 * 표식을 빠뜨리고, 캐시가 만료돼 되살릴 수 없는 경우까지 면제받는다.
 */
export function keepScrollOnEnter(pathname: string) {
  keepScrollFor = pathname
}

/**
 * 화면을 맨 위로 올린다. html에 scroll-behavior: smooth가 걸려 있어 instant를 명시한다 —
 * 그대로 두면 아래에서 위로 흘러올라가, 갈아탄 게 아니라 같은 화면이 움직이는 것처럼 보인다.
 */
export function scrollToTop() {
  window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
}

/**
 * 이동한 뒤 화면을 맨 위로 올린다.
 *
 * SPA는 주소와 컴포넌트만 갈리고 문서의 스크롤은 그대로 남는다. 그래서 홈 하단에서 카드를
 * 누르면 경매방이 열리는데 화면은 이전에 보던 높이에 머문다.
 *
 * 새로 쌓는 이동(PUSH)에서만 올린다.
 * - POP은 브라우저 자체 스크롤 복원이 자리를 맞춘다. 첫 진입도 POP이라 새로고침으로 되살아난
 *   위치를 빼앗지 않는다.
 * - REPLACE는 화면을 갈아타는 이동이 아니라 **같은 화면이 자기 상태를 주소에 적는** 이동이다.
 *   경매 목록은 탭·조건·미리보기 열림을 전부 주소에 replace 로 쓴다. 여기서 올리면 미리보기를
 *   닫기만 해도 목록이 맨 위로 튄다.
 */
export function useScrollReset() {
  // key는 히스토리 항목마다 다르다. pathname으로 보면 같은 주소로 다시 들어가는 이동을 놓치고,
  // 화면 안에서 데이터만 갱신될 때는 key가 그대로여서 위치를 건드리지 않는다
  const { key, pathname } = useLocation()
  const navigationType = useNavigationType()

  useLayoutEffect(() => {
    const keep = keepScrollFor === pathname
    keepScrollFor = null

    if (keep || navigationType !== 'PUSH') return

    scrollToTop()
  }, [key, pathname, navigationType])
}
