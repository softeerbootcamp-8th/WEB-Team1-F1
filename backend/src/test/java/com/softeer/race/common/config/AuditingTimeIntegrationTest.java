package com.softeer.race.common.config;

import com.softeer.race.notification.domain.Notification;
import com.softeer.race.notification.domain.NotificationType;
import com.softeer.race.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.softeer.race.support.IntegrationTestSupport;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 감사 필드가 서버 단일 시각을 쓰는지 저장까지 관통해 검증
 * <p>
 * 1. 시계를 고정한다
 * 2. 엔티티를 저장한다
 * 3. created_at 과 updated_at 이 고정한 시각으로 남는다
 */
@DisplayName("감사 시각 통합 테스트")
class AuditingTimeIntegrationTest extends IntegrationTestSupport {

    // 상수
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);
    private static final long USER_ID = 91L;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("시나리오 1 : 알림 저장 -> 생성 시각과 수정 시각이 고정한 Clock 의 시각으로 남는다")
    @Sql("/sql/auditing-user.sql")
    void scenario1_AuditingFollowsServerClock() {
        // when : 알림 한 건 저장
        Long id = transactionTemplate.execute(status -> {
            Notification notification = Notification.of(
                    entityManager.getReference(User.class, USER_ID),
                    NotificationType.AUCTION_WON,
                    "낙찰되었습니다",
                    1L);
            entityManager.persist(notification);
            return notification.getId();
        });

        // then : DB 에 남은 값이 JVM 기본 시각이 아니라 고정한 Clock 의 시각이다
        Notification saved = transactionTemplate.execute(status ->
                entityManager.find(Notification.class, id));

        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }
}