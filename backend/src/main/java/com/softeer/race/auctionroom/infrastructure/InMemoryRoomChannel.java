package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRoomChannel implements RoomChannel {

    // 등록은 요청 스레드, 해제는 컨테이너 콜백 스레드, 전송은 또 다른 스레드에서 온다
    private final Map<Long, Set<RoomSubscriber>> subscribersByAuction = new ConcurrentHashMap<>();

    @Override
    public void subscribe(long auctionId, RoomSubscriber subscriber) {
        // 집합을 얻는 것과 더하는 것을 한 번에
        // 나누니까 기존 집합을 받아든 사이 마지막 해제가 엔트리를 지워, 맵에서 떨어져 나간 집합에 더하게 된다
        subscribersByAuction.compute(auctionId, (id, subscribers) -> {
            Set<RoomSubscriber> room = subscribers != null ? subscribers : ConcurrentHashMap.newKeySet();
            room.add(subscriber);

            return room;
        });
    }

    @Override
    public void unsubscribe(long auctionId, RoomSubscriber subscriber) {
        remove(auctionId, Set.of(subscriber));
    }

    @Override
    public int countSubscribers(long auctionId) {
        Set<RoomSubscriber> subscribers = subscribersByAuction.get(auctionId);

        return subscribers == null ? 0 : subscribers.size();
    }

    @Override
    public void broadcast(long auctionId, RoomState state) {
        Set<RoomSubscriber> subscribers = subscribersByAuction.get(auctionId);

        if (subscribers == null) {
            return;
        }

        // 순회 중에는 집합을 건드리지 않는다, 보내다 닫힌 구독은 모아 두었다가 끝나고 걷어낸다
        Set<RoomSubscriber> closed = new HashSet<>();

        for (RoomSubscriber subscriber : subscribers) {
            subscriber.send(state);

            if (!subscriber.isOpen()) {
                closed.add(subscriber);
            }
        }

        // 걷어내기는 정리 작업이라 다시 전송하지 않는다, 전송이 제거를 부르고 제거가 다시 전송을 부르면 재귀가 된다
        // 이미 닫힌 구독의 주인은 방을 떠났으므로 줄어든 접속자 수는 다음 사건에서 맞춰진다
        remove(auctionId, closed);
    }

    // 서버는 이 연결에 쓰기만 하고 읽지 않아서, 상대가 끊어도 다음 쓰기 전까지 모른다
    // 아무 사건이 없는 방은 영영 모르므로 주기적으로 찔러 봐야 죽은 구독이 드러난다
    @Override
    public Set<Long> sweepClosed() {
        Set<Long> sweptAuctions = new HashSet<>();

        subscribersByAuction.forEach((auctionId, subscribers) -> {
            Set<RoomSubscriber> closed = new HashSet<>();

            for (RoomSubscriber subscriber : subscribers) {
                subscriber.ping();

                if (!subscriber.isOpen()) {
                    closed.add(subscriber);
                }
            }

            if (!closed.isEmpty()) {
                remove(auctionId, closed);
                sweptAuctions.add(auctionId);
            }
        });

        return sweptAuctions;
    }

    // 호출자가 이 목록을 돌며 방을 끊으므로 도는 사이에 맵이 바뀐다, 그 순간의 사본을 준다
    @Override
    public Set<Long> subscribedAuctions() {
        return Set.copyOf(subscribersByAuction.keySet());
    }

    @Override
    public void closeRoom(long auctionId) {
        // 명부에서 먼저 뺀다, 끝난 연결이 해제 콜백으로 돌아왔을 때 방이 비어 있어야 갱신이 돌지 않는다
        Set<RoomSubscriber> subscribers = subscribersByAuction.remove(auctionId);

        if (subscribers == null) {
            return;
        }

        subscribers.forEach(RoomSubscriber::close);
    }

    // 마지막 구독이 빠지는 순간과 새 구독이 들어오는 순간이 겹쳐도 새 구독이 유실되지 않게 한 번에 처리한다
    private void remove(long auctionId, Set<RoomSubscriber> targets) {
        if (targets.isEmpty()) {
            return;
        }

        subscribersByAuction.computeIfPresent(auctionId, (id, subscribers) -> {
            subscribers.removeAll(targets);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
