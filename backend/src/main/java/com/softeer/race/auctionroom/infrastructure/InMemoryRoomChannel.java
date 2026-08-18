package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auctionroom.application.RoomMessage;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.application.ViewerCount;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InMemoryRoomChannel implements RoomChannel {

    // 등록은 요청 스레드, 해제는 컨테이너 콜백 스레드, 전송은 또 다른 스레드에서 온다
    private final Map<Long, Set<RoomSubscription>> subscriptionsByRoom = new ConcurrentHashMap<>();

    // 방마다 두지 않는다, 전체가 하나여도 방 안에서 단조 증가하면 그 방의 순서를 가리는 데 충분하다
    private final AtomicLong sequence = new AtomicLong();

    // 뒤늦게 붙은 구독에게 줄 마지막 현황이다, 사람 수는 붙는 순간 새로 방송되므로 담지 않는다
    private final Map<Long, RoomState> lastStateByRoom = new ConcurrentHashMap<>();

    @Override
    public void subscribe(long auctionId, RoomSubscription subscription) {
        // 집합을 얻는 것과 더하는 것을 한 번에
        // 나누니까 기존 집합을 받아든 사이 마지막 해제가 엔트리를 지워, 맵에서 떨어져 나간 집합에 더하게 된다
        subscriptionsByRoom.compute(auctionId, (id, subscriptions) -> {
            Set<RoomSubscription> room = subscriptions != null ? subscriptions : ConcurrentHashMap.newKeySet();
            room.add(subscription);

            return room;
        });
    }

    @Override
    public boolean unsubscribe(long auctionId, RoomSubscription subscription) {
        return remove(auctionId, Set.of(subscription));
    }

    @Override
    public int viewerCount(long auctionId) {
        Set<RoomSubscription> subscriptions = subscriptionsByRoom.get(auctionId);

        return subscriptions == null ? 0 : viewerCount(subscriptions);
    }

    // compute 가 키 하나를 직렬화하므로 같은 방을 동시에 읽어도 센 수와 번호가 어긋나지 않는다
    @Override
    public ViewerCount readViewerCount(long auctionId) {
        AtomicReference<ViewerCount> read = new AtomicReference<>();

        subscriptionsByRoom.computeIfPresent(auctionId, (id, subscriptions) -> {
            read.set(new ViewerCount(auctionId, viewerCount(subscriptions), sequence.incrementAndGet()));
            return subscriptions;
        });

        // 방이 명부에서 빠진 뒤라면 남은 구독도 없다, 보낼 곳이 없으므로 번호를 쓰지 않는다
        return read.get() != null ? read.get() : new ViewerCount(auctionId, 0, 0);
    }

    @Override
    public Map<Long, Integer> viewerCountByRoom() {
        Map<Long, Integer> counts = new HashMap<>();

        subscriptionsByRoom.forEach((auctionId, subscriptions) -> counts.put(auctionId, viewerCount(subscriptions)));

        return counts;
    }

    // 구독이 빠지면 그 사람도 같이 사라지므로 사람을 따로 명부에 들고 정리할 것이 없다
    private static int viewerCount(Set<RoomSubscription> subscriptions) {
        Set<Long> viewers = new HashSet<>();

        for (RoomSubscription subscription : subscriptions) {
            viewers.add(subscription.viewerId());
        }

        return viewers.size();
    }

    @Override
    public void broadcast(long auctionId, RoomMessage message) {
        Set<RoomSubscription> subscriptions = subscriptionsByRoom.get(auctionId);

        if (subscriptions == null) {
            return;
        }

        remember(auctionId, message);

        // 순회 중에는 집합을 건드리지 않는다, 보내다 닫힌 구독은 모아 두었다가 끝나고 걷어낸다
        Set<RoomSubscription> closed = new HashSet<>();

        for (RoomSubscription subscription : subscriptions) {
            subscription.send(message);

            if (!subscription.isOpen()) {
                closed.add(subscription);
            }
        }

        // 걷어내기는 정리 작업이라 다시 전송하지 않는다, 전송이 제거를 부르고 제거가 다시 전송을 부르면 재귀가 된다
        // 이미 닫힌 구독의 주인은 방을 떠났으므로 줄어든 사람 수는 다음 사건에서 맞춰진다
        discard(auctionId, closed);
    }

    @Override
    public void catchUp(long auctionId, RoomSubscription subscription) {
        RoomState state = lastStateByRoom.get(auctionId);

        if (state != null) {
            subscription.send(state);
        }
    }

    // 낡은 것이 뒤늦게 덮어쓰지 않게 한다, 사서함이 구독마다 하는 판정을 방 단위로 한 번 더 하는 것이다
    private void remember(long auctionId, RoomMessage message) {
        if (!(message instanceof RoomState state)) {
            return;
        }

        lastStateByRoom.merge(auctionId, state, (kept, arrived) -> arrived.isStalerThan(kept) ? kept : arrived);
    }

    // 서버는 이 연결에 쓰기만 하고 읽지 않아서, 상대가 끊어도 다음 쓰기 전까지 모른다
    // 아무 사건이 없는 방은 영영 모르므로 주기적으로 찔러 봐야 죽은 구독이 드러난다
    @Override
    public Set<Long> sweepClosed() {
        Set<Long> sweptRooms = new HashSet<>();

        subscriptionsByRoom.forEach((auctionId, subscriptions) -> {
            Set<RoomSubscription> closed = new HashSet<>();

            for (RoomSubscription subscription : subscriptions) {
                subscription.ping();

                if (!subscription.isOpen()) {
                    closed.add(subscription);
                }
            }

            if (!closed.isEmpty()) {
                discard(auctionId, closed);
                sweptRooms.add(auctionId);
            }
        });

        return sweptRooms;
    }

    // 호출자가 이 목록을 돌며 방을 끊으므로 도는 사이에 맵이 바뀐다, 그 순간의 사본을 준다
    @Override
    public Set<Long> subscribedRooms() {
        return Set.copyOf(subscriptionsByRoom.keySet());
    }

    // 순회를 여기 둔다, 밖에서 돌면 그사이 새로 생긴 방을 놓친다
    @Override
    public void closeAll() {
        subscribedRooms().forEach(this::closeRoom);
    }

    @Override
    public void closeRoom(long auctionId) {
        // 명부에서 먼저 뺀다, 끝난 연결이 해제 콜백으로 돌아왔을 때 방이 비어 있어야 갱신이 돌지 않는다
        Set<RoomSubscription> subscriptions = subscriptionsByRoom.remove(auctionId);
        lastStateByRoom.remove(auctionId);

        if (subscriptions == null) {
            return;
        }

        subscriptions.forEach(RoomSubscription::close);
    }

    // 상대가 사라진 구독을 걷어낸다, 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
    // 해제는 이 순서를 지킨다, 명부를 먼저 비워야 끝난 연결의 콜백이 돌아왔을 때 갱신이 돌지 않는다
    private void discard(long auctionId, Set<RoomSubscription> closed) {
        remove(auctionId, closed);
        closed.forEach(RoomSubscription::close);
    }

    // 마지막 구독이 빠지는 순간과 새 구독이 들어오는 순간이 겹쳐도 새 구독이 유실되지 않게 한 번에 처리한다
    // 실제로 뺀 것이 있으면 참, 명부에 없던 것만 넘어오면 거짓이다
    private boolean remove(long auctionId, Set<RoomSubscription> targets) {
        if (targets.isEmpty()) {
            return false;
        }

        // 판정을 잠금 안에서 해야 뺀 사람과 뺐다고 답하는 사람이 어긋나지 않는다
        AtomicBoolean removed = new AtomicBoolean();

        subscriptionsByRoom.computeIfPresent(auctionId, (id, subscriptions) -> {
            removed.set(subscriptions.removeAll(targets));

            if (subscriptions.isEmpty()) {
                // 방이 명부에서 빠지는 자리가 여기와 closeRoom 둘뿐이다, 안 지우면 끝난 방의 현황이 남는다
                lastStateByRoom.remove(auctionId);

                return null;
            }

            return subscriptions;
        });

        return removed.get();
    }
}
