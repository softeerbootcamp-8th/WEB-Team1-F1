package com.softeer.race.auctionroom.domain;

/**
 * 경매방의 진행 단계
 */
public enum RoomPhase {
    NOT_OPEN(false),
    WAITING(true),
    LIVE(true),
    RESULT(true),
    CLOSED(false);

    private final boolean connectionAllowed;

    RoomPhase(boolean connectionAllowed) {
        this.connectionAllowed = connectionAllowed;
    }

    /**
     * 연결을 열어 두는 단계, 이 단계에서만 구독을 받고 접속자로 센다
     */
    public boolean allowsConnection() {
        return connectionAllowed;
    }
}
