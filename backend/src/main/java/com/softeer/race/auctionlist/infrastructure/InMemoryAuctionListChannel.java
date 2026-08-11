package com.softeer.race.auctionlist.infrastructure;

import com.softeer.race.auctionlist.application.AuctionListChannel;
import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class InMemoryAuctionListChannel implements AuctionListChannel {

    // 등록은 요청 스레드, 해제는 컨테이너 콜백 스레드, 전송은 커밋한 스레드에서 온다
    // 경매방처럼 compute 로 감싸지 않는다, 명부가 하나뿐이라 마지막 구독이 빠져도 지울 엔트리가 없다
    private final Set<AuctionListSubscriber> subscribers = ConcurrentHashMap.newKeySet();

    @Override
    public void subscribe(AuctionListSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void unsubscribe(AuctionListSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    @Override
    public void broadcastCard(AuctionCardInfo card) {
        forEachOpen(subscriber -> subscriber.sendCard(card));
    }

    @Override
    public void broadcastAudience(long auctionId, int connectedCount) {
        forEachOpen(subscriber -> subscriber.sendAudience(auctionId, connectedCount));
    }

    // 서버는 이 연결에 쓰기만 하고 읽지 않아, 상대가 끊어도 다음 쓰기 전까지 모른다
    // 목록은 몇 분 아무 일도 없는 것이 정상이라 찔러 보지 않으면 죽은 구독이 영영 드러나지 않는다
    @Override
    public void sweepClosed() {
        forEachOpen(AuctionListSubscriber::ping);
    }

    @Override
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    // 보낼 것이 둘로 늘었다, 걷어내기를 각자 하면 한 곳만 빠뜨려도 그 응답이 만료까지 살아남는다
    private void forEachOpen(Consumer<AuctionListSubscriber> send) {
        Set<AuctionListSubscriber> closed = new HashSet<>();

        for (AuctionListSubscriber subscriber : subscribers) {
            send.accept(subscriber);

            if (!subscriber.isOpen()) {
                closed.add(subscriber);
            }
        }

        // 명부를 먼저 비운다, close 가 부르는 해제 콜백이 돌아왔을 때 할 일이 남아 있으면 안 된다
        subscribers.removeAll(closed);

        // 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
        closed.forEach(AuctionListSubscriber::close);
    }
}