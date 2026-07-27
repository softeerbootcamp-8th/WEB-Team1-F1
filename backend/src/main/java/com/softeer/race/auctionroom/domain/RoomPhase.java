package com.softeer.race.auctionroom.domain;

import lombok.Getter;

/**
 * 경매방의 진행 단계
 */
@Getter
public enum RoomPhase {
    NOT_OPEN(false),
    WAITING(true),
    LIVE(true),
    RESULT(true),
    CLOSED(false);

    /**
     * 접속자로 집계하는 구간
     */
    private final boolean open;

    RoomPhase(boolean open) {
        this.open = open;
    }
}
