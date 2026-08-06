package com.softeer.race.notification.application;

import com.softeer.race.notification.domain.Notification;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 구독 등록과 되돌리기
 * <p>
 * 스프링을 띄우지 않는다. 검증할 것이 "건수 조회가 실패했을 때 등록을 되돌리는가" 하나이고,
 * 그러려면 조회가 던져야 하는데 실물 DB 로는 그 상황을 만들기 번거롭다. 이 서비스는 협력자가 둘뿐이라
 * 직접 세우면 되고, 컨텍스트를 하나 더 띄우지 않아 전체 테스트 시간도 늘지 않는다.
 */
class NotificationStreamServiceTest {

    private static final long USER = 1L;

    private final FakeUserChannel channel = new FakeUserChannel();

    @Test
    @DisplayName("건수 조회가 실패하면 등록한 구독을 되돌리고 예외를 올린다")
    void rollsBackSubscriptionWhenUnreadCountFails() {
        // given : 조회가 실패하는 상황이다 (커넥션 고갈·DB 장애)
        NotificationStreamService service =
                new NotificationStreamService(channel, new BrokenNotificationRepository());
        FakeSubscriber subscriber = new FakeSubscriber();

        // when
        Throwable thrown = catchThrowable(() -> service.subscribe(USER, subscriber));

        // then 1 : 스트림이 열리지 않았음을 클라이언트가 알아야 한다, 삼키면 배지가 틀린 채로 남는다
        assertThat(thrown).isInstanceOf(IllegalStateException.class);

        // then 2 : 채널에 남지 않는다
        // 남으면 걷어낼 방법이 없다 — 컨트롤러가 예외로 끝나 해제 콜백이 붙지 못하고,
        // 초기화되지 않은 emitter 는 찔러 봐도 예외를 내지 않아 청소 대상으로도 드러나지 않는다
        assertThat(channel.subscribed).isEmpty();
    }

    @Test
    @DisplayName("조회가 되면 구독이 남고 안 읽은 건수를 한 번 보낸다")
    void keepsSubscriptionAndSendsUnreadCount() {
        // given : 위 시나리오의 대조군이다, 성공 경로에서는 되돌리지 않는다
        NotificationStreamService service =
                new NotificationStreamService(channel, new FixedNotificationRepository(3L));
        FakeSubscriber subscriber = new FakeSubscriber();

        // when
        service.subscribe(USER, subscriber);

        // then
        assertThat(channel.subscribed).containsExactly(subscriber);
        assertThat(subscriber.unreadCounts).containsExactly(3L);
    }

    private static final class FakeUserChannel implements UserChannel {

        private final List<UserSubscriber> subscribed = new ArrayList<>();

        @Override
        public void subscribe(long userId, UserSubscriber subscriber) {
            subscribed.add(subscriber);
        }

        @Override
        public void unsubscribe(long userId, UserSubscriber subscriber) {
            subscribed.remove(subscriber);
        }

        @Override
        public void send(long userId, NotificationPush push) {
            throw new UnsupportedOperationException("이 테스트는 전송 경로를 지나지 않는다");
        }

        @Override
        public void sweepClosed() {
            throw new UnsupportedOperationException("이 테스트는 청소 경로를 지나지 않는다");
        }
    }

    private static final class FakeSubscriber implements UserSubscriber {

        private final List<Long> unreadCounts = new ArrayList<>();

        @Override
        public void send(NotificationPush push) {
            throw new UnsupportedOperationException("이 테스트는 전송 경로를 지나지 않는다");
        }

        @Override
        public void sendUnreadCount(long unreadCount) {
            unreadCounts.add(unreadCount);
        }

        @Override
        public void ping() {
            throw new UnsupportedOperationException("이 테스트는 청소 경로를 지나지 않는다");
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException("해제는 응답을 끝내지 않는다");
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    // 구독 등록 경로가 쓰는 것은 countUnread 하나뿐이라 나머지는 불리면 안 되는 것으로 둔다
    private abstract static class StubNotificationRepository implements NotificationRepository {

        @Override
        public Notification save(Notification notification) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NotificationRow> findPage(long userId, long cursor, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markRead(long id, long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markAllRead(long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class BrokenNotificationRepository extends StubNotificationRepository {

        @Override
        public long countUnread(long userId) {
            throw new IllegalStateException("조회 실패");
        }
    }

    private record FixedNotificationRepository(long unreadCount) implements NotificationRepository {

        @Override
        public long countUnread(long userId) {
            return unreadCount;
        }

        @Override
        public Notification save(Notification notification) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NotificationRow> findPage(long userId, long cursor, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markRead(long id, long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markAllRead(long userId) {
            throw new UnsupportedOperationException();
        }
    }
}
