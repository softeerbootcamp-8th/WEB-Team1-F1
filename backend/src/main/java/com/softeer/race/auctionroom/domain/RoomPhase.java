package com.softeer.race.auctionroom.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 경매방의 진행 단계
 */
public enum RoomPhase {
    NOT_OPEN(AuctionRoomErrorCode.ROOM_NOT_OPEN_YET),
    WAITING(null),
    LIVE(null),
    RESULT(null),
    CLOSED(AuctionRoomErrorCode.ROOM_ALREADY_CLOSED);

    // 마감 후 결과를 확인할 수 있는 구간, 기획에서 정한 값이다
    private static final Duration RESULT_VIEWING = Duration.ofMinutes(5);

    private final AuctionRoomErrorCode entryRejection;

    RoomPhase(AuctionRoomErrorCode entryRejection) {
        this.entryRejection = entryRejection;
    }

    /**
     * 주어진 시각 기준의 방 단계
     */
    public static RoomPhase at(LocalDateTime now,
                               LocalDateTime roomOpenAt,
                               LocalDateTime startTime,
                               LocalDateTime currentEndTime) {
        if (now.isBefore(roomOpenAt)) {
            return NOT_OPEN;
        }
        if (now.isBefore(startTime)) {
            return WAITING;
        }
        if (now.isBefore(currentEndTime)) {
            return LIVE;
        }
        if (now.isBefore(currentEndTime.plus(RESULT_VIEWING))) {
            return RESULT;
        }
        return CLOSED;
    }

    /**
     * 방을 열어 줄 수 없는 사유, 열어 주는 단계에는 없다
     */
    public Optional<AuctionRoomErrorCode> entryRejection() {
        return Optional.ofNullable(entryRejection);
    }

    /**
     * 연결을 열어 두는 단계, 이 단계에서만 구독을 받고 접속자로 센다
     */
    public boolean allowsConnection() {
        return entryRejection == null;
    }

    /**
     * 개장 안내를 열어 줄 수 없는 사유, 아직 열리지 않은 단계에는 없다
     */
    // 방 입장과 반대 방향의 거절이다, 세 문의 거절 사유를 여기 모아 호출부가 같은 모양으로 읽히게 한다
    public Optional<AuctionRoomErrorCode> openingRejection() {
        return this == NOT_OPEN
                ? Optional.empty()
                : Optional.of(AuctionRoomErrorCode.ROOM_ALREADY_OPEN);
    }
}
