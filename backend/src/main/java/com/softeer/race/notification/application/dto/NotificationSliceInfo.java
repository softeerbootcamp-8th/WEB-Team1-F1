package com.softeer.race.notification.application.dto;

import com.softeer.race.notification.domain.NotificationRow;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 목록 한 페이지
 * <p>
 * 조회 결과를 다시 감싸지 않고 NotificationRow 를 그대로 담는다. 경매글 목록은 단계와 접속자 수를
 * 서비스가 계산해 붙이느라 별도 Info 가 필요했지만, 알림은 서비스가 더할 값이 없어 한 겹을 더 두면
 * 같은 필드를 두 번 나열하는 것으로 끝난다.
 */
public record NotificationSliceInfo(
        List<NotificationRow> content,
        LocalDateTime serverTime,
        boolean hasNext,
        Long nextCursor
) {
}