package com.softeer.race.auctionlist.infrastructure;

import com.softeer.race.auctionlist.application.AuctionListChannel;
import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        Set<AuctionListSubscriber> closed = new HashSet<>();

        for (AuctionListSubscriber subscriber : subscribers) {
            subscriber.sendCard(card);

            if (!subscriber.isOpen()) {
                closed.add(subscriber);
            }
        }

        // 명부를 먼저 비운다, close 가 부르는 해제 콜백이 돌아왔을 때 할 일이 남아 있으면 안 된다
        subscribers.removeAll(closed);

        // 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
        closed.forEach(AuctionListSubscriber::close);
    }

    @Override
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }
}