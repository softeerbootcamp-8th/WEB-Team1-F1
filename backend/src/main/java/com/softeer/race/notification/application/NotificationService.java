package com.softeer.race.notification.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.notification.application.dto.NotificationSliceInfo;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.notification.exception.NotificationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    // 드롭다운과 전체 목록이 같은 크기를 쓴다. 크기를 클라이언트가 정하게 하면 상한 검증이 따라오는데,
    // 알림 한 줄은 그 비용을 낼 만큼 무겁지 않다
    private static final int PAGE_SIZE = 10;

    // 첫 페이지는 커서가 없다. id 로 끊어 읽으므로 "아직 아무것도 안 봤다"가 가장 큰 id 로 표현된다
    private static final long FIRST_CURSOR = Long.MAX_VALUE;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * 내 알림 한 페이지, 커서가 없으면 첫 페이지
     */
    public NotificationSliceInfo list(long userId, Long cursor) {
        long from = (cursor != null) ? cursor : FIRST_CURSOR;

        // 한 건 더 읽어 다음 페이지 유무를 판단한다. 전체를 세는 쿼리를 피하려는 것이다
        List<NotificationRow> found =
                notificationRepository.findPage(userId, from, Limit.of(PAGE_SIZE + 1));

        boolean hasNext = found.size() > PAGE_SIZE;
        List<NotificationRow> page = hasNext ? found.subList(0, PAGE_SIZE) : found;

        // 내림차순이라 마지막 행의 id 가 다음 페이지의 시작점이다
        Long nextCursor = hasNext ? page.getLast().id() : null;

        return new NotificationSliceInfo(page, LocalDateTime.now(clock), hasNext, nextCursor);
    }

    public long countUnread(long userId) {
        return notificationRepository.countUnread(userId);
    }

    /**
     * 알림 한 건을 읽음으로, 이미 읽은 알림에 다시 요청해도 성공한다
     */
    @Transactional
    public void markRead(long userId, long notificationId) {
        // 0건은 없는 알림이거나 남의 알림이다. 어느 쪽인지 알려 주지 않는다
        if (notificationRepository.markRead(notificationId, userId) == 0) {
            throw new BusinessException(NotificationErrorCode.NOT_FOUND);
        }
    }

    /**
     * 내 알림을 모두 읽음으로, 읽을 것이 없어도 성공한다
     */
    @Transactional
    public void markAllRead(long userId) {
        notificationRepository.markAllRead(userId);
    }
}