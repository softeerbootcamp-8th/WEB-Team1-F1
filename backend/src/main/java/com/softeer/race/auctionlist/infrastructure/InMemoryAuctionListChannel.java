package com.softeer.race.auctionlist.infrastructure;

import com.softeer.race.auctionlist.application.AuctionListChannel;
import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryAuctionListChannel implements AuctionListChannel {

    // 등록은 요청 스레드, 해제는 컨테이너 콜백 스레드, 전송은 커밋한 스레드나 스케줄러에서 온다
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
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }
}