package com.softeer.race.notification.infrastructure;

import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.UserChannel;
import com.softeer.race.notification.application.UserSubscriber;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryUserChannel implements UserChannel {

    // 등록은 요청 스레드, 해제는 컨테이너 콜백 스레드, 전송은 커밋한 스레드(요청 스레드거나 스케줄러)에서 온다
    private final Map<Long, Set<UserSubscriber>> subscribersByUser = new ConcurrentHashMap<>();

    @Override
    public void subscribe(long userId, UserSubscriber subscriber) {
        // 집합을 얻는 것과 더하는 것을 한 번에 한다
        // 나누면 기존 집합을 받아든 사이 마지막 해제가 엔트리를 지워, 맵에서 떨어져 나간 집합에 더하게 된다
        // 탭 하나만 열어 둔 회원은 새로 고칠 때마다 이 경로를 밟아서 방보다 겹칠 일이 잦다
        subscribersByUser.compute(userId, (id, subscribers) -> {
            Set<UserSubscriber> opened =
                    subscribers != null ? subscribers : ConcurrentHashMap.newKeySet();
            opened.add(subscriber);

            return opened;
        });
    }

    @Override
    public void unsubscribe(long userId, UserSubscriber subscriber) {
        remove(userId, Set.of(subscriber));
    }

    @Override
    public void send(long userId, NotificationPush push) {
        Set<UserSubscriber> subscribers = subscribersByUser.get(userId);

        // 접속하지 않은 회원이다, 보낼 곳이 없는 것은 실패가 아니다
        if (subscribers == null) {
            return;
        }

        // 순회 중에는 집합을 건드리지 않는다, 보내다 닫힌 구독은 모아 두었다가 끝나고 걷어낸다
        Set<UserSubscriber> closed = new HashSet<>();

        for (UserSubscriber subscriber : subscribers) {
            subscriber.send(push);

            if (!subscriber.isOpen()) {
                closed.add(subscriber);
            }
        }

        // 걷어내기는 정리 작업이라 다시 전송하지 않는다
        remove(userId, closed);
    }

    // 서버는 이 연결에 쓰기만 하고 읽지 않아서, 상대가 끊어도 다음 쓰기 전까지 모른다
    // 알림은 몇 시간 아무 일도 없는 것이 정상이라, 찔러 보지 않으면 죽은 구독이 영영 드러나지 않는다
    @Override
    public void sweepClosed() {
        subscribersByUser.forEach((userId, subscribers) -> {
            Set<UserSubscriber> closed = new HashSet<>();

            for (UserSubscriber subscriber : subscribers) {
                subscriber.ping();

                if (!subscriber.isOpen()) {
                    closed.add(subscriber);
                }
            }

            remove(userId, closed);
        });
    }

    // 마지막 구독이 빠지는 순간과 새 구독이 들어오는 순간이 겹쳐도 새 구독이 유실되지 않게 한 번에 처리한다
    private void remove(long userId, Set<UserSubscriber> targets) {
        if (targets.isEmpty()) {
            return;
        }

        subscribersByUser.computeIfPresent(userId, (id, subscribers) -> {
            subscribers.removeAll(targets);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}