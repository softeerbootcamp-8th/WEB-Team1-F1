package com.softeer.race.auctionroom.domain;

import java.time.LocalDateTime;

/**
 * 경매방 접속자 집계
 */
public interface RoomPresence {

    /**
     * 사용자를 접속자로 기록
     */
    void markPresent(long auctionId, long userId, LocalDateTime now);

    /**
     * 유효시간 안에 기록이 있는 접속자 수
     */
    int countPresent(long auctionId, LocalDateTime now);
}