import { useEffect, useState } from 'react'

/**
 * 이 탭이 지금 화면에 보이는지. 보지 않는 사람이 실시간 연결을 물고 있지 않게 하는 데 쓴다.
 *
 * blur·focus 는 보지 않는다. 다른 창을 클릭했어도 이 탭이 화면에 보이면 사람은 보고 있는
 * 것이고, visibilityState 가 그 구분을 이미 한다. 둘을 섞으면 창만 옮겨도 연결이 끊긴다.
 */
export function useDocumentVisible(): boolean {
  const [visible, setVisible] = useState(() => document.visibilityState === 'visible')

  useEffect(() => {
    const update = () => setVisible(document.visibilityState === 'visible')

    document.addEventListener('visibilitychange', update)
    return () => document.removeEventListener('visibilitychange', update)
  }, [])

  return visible
}
