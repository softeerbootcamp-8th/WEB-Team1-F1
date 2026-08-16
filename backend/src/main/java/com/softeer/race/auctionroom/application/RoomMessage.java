package com.softeer.race.auctionroom.application;

/**
 * 열린 구독으로 흘려보내는 한 건
 */
public sealed interface RoomMessage permits RoomState {

    /**
     * 같은 종류로 이미 내보낸 것보다 낡았는지, 내보낸 것이 없으면 낡지 않은 것으로 본다
     */
    boolean isStalerThan(RoomMessage sent);
}
