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
import org.springframework.context.ApplicationEventPublisher;

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
    private final ApplicationEventPublisher eventPublisher;
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

        publishUnreadCount(userId);
    }

    /**
     * 내 알림을 모두 읽음으로, 읽을 것이 없어도 성공한다
     */
    @Transactional
    public void markAllRead(long userId) {
        // 바뀐 것이 없으면 알릴 것도 없다. 갱신 건수로 판별되는 쪽만 거른다 —
        // 한 건 읽기는 이미 읽은 알림에 다시 요청해도 조건에 맞는 행이 있어 1 이 온다
        if (notificationRepository.markAllRead(userId) == 0) {
            return;
        }

        publishUnreadCount(userId);
    }

    /**
     * 읽음이 바뀌었음을 남긴다, 전달은 커밋 뒤에 일어난다
     * <p>
     * 건수를 여기서 센다. 커밋 뒤에 세면 끝난 트랜잭션 밖에서 커넥션을 다시 얻어야 한다.
     * 갱신이 롤백되면 이 사건도 전달되지 않으므로 없던 건수가 화면에 남지 않는다.
     */
    private void publishUnreadCount(long userId) {
        eventPublisher.publishEvent(
                new UnreadCountChanged(userId, notificationRepository.countUnread(userId)));
    }
}