/**
 * 운영 화면에서 서버가 보내는 시각을 그대로 자른다. ex) "2026-07-01T10:00:00" → "2026.07.01 10:00"
 *
 * lib/format 의 formatDateTime 을 쓰지 않는다. 그쪽은 new Date 로 파싱하는데, 서버가 보내는
 * LocalDateTime 에는 시간대가 없어 실행 환경에 따라 해석이 갈린다. 운영 화면은 접수·가입 시각을
 * 서버가 적은 그대로 읽어야 하므로 파싱하지 않고 자리만 자른다.
 */
export function formatServerDateTime(value: string): string {
  const [date, time = ''] = value.split('T')

  return `${date.replaceAll('-', '.')} ${time.slice(0, 5)}`.trim()
}
