package com.softeer.race.notification.infrastructure;

import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.NotificationDeliveryMetrics;
import com.softeer.race.notification.application.UserChannel;
import com.softeer.race.notification.application.UserSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.softeer.race.notification.application.NotificationDeliveryMetrics.Event.HEARTBEAT;
import static com.softeer.race.notification.application.NotificationDeliveryMetrics.Event.NOTIFICATION;
import static com.softeer.race.notification.application.NotificationDeliveryMetrics.Event.UNREAD_COUNT;

@Component
@RequiredArgsConstructor
public class InMemoryUserChannel implements UserChannel {

    private final NotificationDeliveryMetrics deliveryMetrics;

    // 등록은 요청 스레드, 해제는 컨테이너 콜백 스레드에서 온다.
    // 알림·건수 전송은 전용 실행기, 하트비트는 알림 스트림 스케줄러에서 와 서로 겹칠 수 있다.
    private final Map<Long, Set<UserSubscriber>> subscribersByUser = new ConcurrentHashMap<>();

    @Override
    public void subscribe(long userId, UserSubscriber subscriber) {
        // 집합을 얻는 것과 더하는 것을 한 번에 한다
        // 나누면 기존 집합을 받아든 사이 마지막 해제가 엔트리를 지워, 맵에서 떨어져 나간 집합에 더하게 된다
        // 탭 하나만 열어 둔 회원은 새로 고칠 때마다 이 경로를 밟아서 방보다 겹칠 일이 잦다
        subscribersByUser.compute(userId, (id, subscribers) -> {
            Set<UserSubscriber> opened =
                    subscribers != null ? subscribers : ConcurrentHashMap.newKeySet();
            if (opened.add(subscriber)) {
                deliveryMetrics.connectionOpened();
            }

            return opened;
        });
    }

    @Override
    public void unsubscribe(long userId, UserSubscriber subscriber) {
        remove(userId, Set.of(subscriber));
    }

    @Override
    public void send(long userId, NotificationPush push) {
        forEachOpen(userId, NOTIFICATION, subscriber -> subscriber.send(push));
    }

    @Override
    public void sendUnreadCount(long userId, long unreadCount) {
        forEachOpen(userId, UNREAD_COUNT, subscriber -> subscriber.sendUnreadCount(unreadCount));
    }

    // 서버는 이 연결에 쓰기만 하고 읽지 않아서, 상대가 끊어도 다음 쓰기 전까지 모른다
    // 알림은 몇 시간 아무 일도 없는 것이 정상이라, 찔러 보지 않으면 죽은 구독이 영영 드러나지 않는다
    @Override
    public void sweepClosed() {
        // 키만 훑고 전송은 위와 같은 길로 보낸다, 걷어내기가 한 곳에만 있게
        subscribersByUser.keySet().forEach(userId -> forEachOpen(userId, HEARTBEAT, UserSubscriber::ping));
    }

    // 보낼 곳이 셋으로 늘었다. 정리를 각자 하면 한 곳만 빠뜨려도 그 응답이 만료까지 살아남는다
    private void forEachOpen(
            long userId,
            NotificationDeliveryMetrics.Event event,
            Consumer<UserSubscriber> action) {
        Set<UserSubscriber> subscribers = subscribersByUser.get(userId);

        // 접속하지 않은 회원이다, 보낼 곳이 없는 것은 실패가 아니다
        if (subscribers == null) {
            return;
        }

        // 순회 중에는 집합을 건드리지 않는다, 보내다 닫힌 구독은 모아 두었다가 끝나고 걷어낸다
        Set<UserSubscriber> closed = new HashSet<>();

        for (UserSubscriber subscriber : subscribers) {
            if (subscriber.isOpen()) {
                deliveryMetrics.recordSend(
                        event,
                        () -> action.accept(subscriber),
                        subscriber::isOpen);
            }

            if (!subscriber.isOpen()) {
                closed.add(subscriber);
            }
        }

        // 걷어내기는 정리 작업이라 다시 전송하지 않는다
        discard(userId, closed);
    }

    // 상대가 사라진 구독을 걷어낸다, 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
    // 순서를 지킨다, 명부를 먼저 비워야 끝낸 연결의 해제 콜백이 돌아왔을 때 할 일이 없다
    private void discard(long userId, Set<UserSubscriber> closed) {
        remove(userId, closed);
        closed.forEach(UserSubscriber::close);
    }

    // 마지막 구독이 빠지는 순간과 새 구독이 들어오는 순간이 겹쳐도 새 구독이 유실되지 않게 한 번에 처리한다
    private void remove(long userId, Set<UserSubscriber> targets) {
        if (targets.isEmpty()) {
            return;
        }

        subscribersByUser.computeIfPresent(userId, (id, subscribers) -> {
            int before = subscribers.size();
            subscribers.removeAll(targets);
            deliveryMetrics.connectionsClosed(before - subscribers.size());
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
