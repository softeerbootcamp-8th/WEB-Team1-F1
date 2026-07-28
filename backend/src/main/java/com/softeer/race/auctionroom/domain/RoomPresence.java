package com.softeer.race.auctionroom.domain;

import java.time.LocalDateTime;

/**
 * 경매방 접속자 집계
 */
public interface RoomPresence {

    /**
     * 사용자를 접속자로 기록, 집계보다 먼저 호출
     */
    void markPresent(long auctionId, long userId, LocalDateTime now);

    /**
     * 유효시간 안의 접속자 수, 기록을 변경하지 않는 순수 조회
     */
    int countPresent(long auctionId, LocalDateTime now);
}