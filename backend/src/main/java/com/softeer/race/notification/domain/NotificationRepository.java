package com.softeer.race.notification.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 회원 알림 보관과 조회
 * <p>
 * JpaRepository 가 아니라 Repository 를 상속해 필요한 메서드만 연다. 알림은 사용자가 지우는 것이
 * 아니라 사건이 남기는 기록이라, 삭제가 열려 있을 이유가 없다.
 */
public interface NotificationRepository extends Repository<Notification, Long> {

    Notification save(Notification notification);

    /**
     * 내 알림을 최근 것부터, 커서 다음부터
     * <p>
     * 정렬과 커서를 모두 id 로 잡는다. 생성 시각으로 잡으면 같은 초에 생긴 알림들의 순서가 정해지지
     * 않아, 읽는 도중 새 알림이 쌓이면 중복이나 누락이 생긴다. id 는 늘기만 하므로 "이 값보다 작은
     * 것"이 곧 "아직 안 본 다음 페이지"가 된다.
     * <p>
     * n.user.id 는 연관 엔티티를 가져오지 않는다. 하이버네이트가 조인 없이 FK 컬럼을 그대로 읽는다.
     */
    @Query("""
            select new com.softeer.race.notification.domain.NotificationRow(
                n.id, n.type, n.message, n.isRead, n.referenceId, n.createdAt)
            from Notification n
            where n.user.id = :userId and n.id < :cursor
            order by n.id desc
            """)
    List<NotificationRow> findPage(@Param("userId") long userId,
                                   @Param("cursor") long cursor,
                                   Limit limit);

    /**
     * 안 읽은 건수
     * <p>
     * 메서드 이름으로 유도하지 않고 쿼리를 적는다. isRead 는 Is 로 시작해서 이름 규칙의 Is 키워드와
     * 겹치고, 어떻게 끊어 읽히는지가 이름만 봐서는 드러나지 않는다.
     */
    @Query("select count(n) from Notification n where n.user.id = :userId and n.isRead = false")
    long countUnread(@Param("userId") long userId);

    /**
     * 알림 한 건을 읽음으로
     * <p>
     * 이미 읽은 알림도 대상에 넣는다. 빼면 두 번째 요청이 0건이 되어 없는 알림과 구별되지 않고,
     * 같은 요청을 두 번 보냈다는 이유로 실패하게 된다.
     * <p>
     * 소유자 조건이 WHERE 에 들어 있어 남의 알림은 0건이 된다. 불러와서 비교하지 않으므로 조회가
     * 한 번 줄고, "남의 것은 없는 것으로 답한다"가 조건식 하나로 표현된다.
     *
     * @return 바뀐 건수, 0이면 없는 알림이거나 남의 알림이다
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.id = :id and n.user.id = :userId")
    int markRead(@Param("id") long id, @Param("userId") long userId);

    /**
     * 내 알림을 모두 읽음으로
     * <p>
     * 여기는 반대로 안 읽은 것만 고른다. 두 번 불러도 결과가 같아서 조건을 좁혀도 실패하지 않고,
     * 이미 읽은 알림까지 건드리면 쌓인 건수만큼 쓰기가 늘어난다.
     *
     * @return 바뀐 건수, 0이어도 정상이다 (읽을 것이 없었을 뿐)
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.user.id = :userId and n.isRead = false")
    int markAllRead(@Param("userId") long userId);
}