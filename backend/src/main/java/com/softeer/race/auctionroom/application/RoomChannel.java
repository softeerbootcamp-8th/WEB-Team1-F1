package com.softeer.race.auctionroom.application;

import java.util.Map;
import java.util.Set;

/**
 * 경매방별로 열려 있는 구독을 모아 두고 현황을 흘려보내는 채널
 */
public interface RoomChannel {

    /**
     * 구독을 방에 등록한다, 같은 구독을 두 번 등록해도 하나다
     */
    void subscribe(long auctionId, RoomSubscription subscription);

    /**
     * 구독을 방에서 뺀다, 두 번 빼도 명부는 같고 실제로 뺀 호출만 참을 돌려준다
     */
    // 걷어내기가 먼저 빼 간 뒤에 해제 콜백이 돌아오므로, 호출자는 자기가 뺀 것인지 알아야
    // 갱신을 두 번 돌리지 않는다
    boolean unsubscribe(long auctionId, RoomSubscription subscription);

    /**
     * 방을 보고 있는 사람 수, 한 사람이 창을 여럿 열어도 하나로 센다
     */
    int viewerCount(long auctionId);

    /**
     * 방을 보고 있는 사람 수를 순번과 함께 읽는다, 세는 것과 번호를 매기는 것이 한 번에 일어난다
     */
    // 나누면 두 스레드가 각자 센 뒤 순서가 뒤집혀, 늦게 도착한 낡은 수가 화면에 남는다
    ViewerCount readViewerCount(long auctionId);

    /**
     * 구독이 있는 방마다의 사람 수, 아무도 없는 방은 담기지 않는다
     */
    // 목록이 subscribedRooms 로 훑고 방마다 세면 경매방의 정리용 순회 수단을 밖에서 쓰게 된다
    Map<Long, Integer> viewerCountByRoom();

    /**
     * 방의 모든 구독에 한 건 전송, 닫힌 구독은 순회가 끝난 뒤 걷어낸다
     */
    void broadcast(long auctionId, RoomMessage message);

    /**
     * 방의 마지막 현황을 구독 하나에만 보낸다, 아직 나간 현황이 없으면 보내지 않는다
     */
    void catchUp(long auctionId, RoomSubscription subscription);

    /**
     * 모든 방을 찔러 보고 닫힌 구독을 걷어낸다, 실제로 걷어낸 방의 식별자를 돌려준다
     */
    Set<Long> sweepClosed();

    /**
     * 구독이 등록된 방의 식별자, 아직 걷히지 않은 죽은 구독도 방을 목록에 남긴다
     */
    Set<Long> subscribedRooms();

    /**
     * 방의 모든 구독을 서버가 끝낸다, 명부에서 먼저 빼므로 끝내는 동안 갱신이 돌지 않는다
     */
    // 돌아온 시점에 연결이 다 끝나 있지는 않다, 구독마다 맡겨 둔 것을 내보낸 뒤에 끝난다
    void closeRoom(long auctionId);
}
