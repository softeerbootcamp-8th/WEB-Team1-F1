package com.softeer.race.auctionroom.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 경매방의 진행 단계, 방 조회와 스트림과 목록 카드 세 응답에 이 이름 그대로 실린다
 */
public enum RoomPhase {
    NOT_OPEN,
    WAITING,
    LIVE,
    RESULT,
    CLOSED;

    // 마감 후 결과를 확인할 수 있는 구간, 기획에서 정한 값이다
    private static final Duration RESULT_VIEWING = Duration.ofMinutes(5);

    /**
     * 주어진 시각 기준의 방 단계
     */
    public static RoomPhase at(LocalDateTime now,
                               LocalDateTime openAt,
                               LocalDateTime startAt,
                               LocalDateTime endAt) {
        if (now.isBefore(openAt)) {
            return NOT_OPEN;
        }
        if (now.isBefore(startAt)) {
            return WAITING;
        }
        if (now.isBefore(endAt)) {
            return LIVE;
        }
        if (now.isBefore(resultViewingEndsAt(endAt))) {
            return RESULT;
        }
        return CLOSED;
    }

    /**
     * 결과를 볼 수 있는 구간이 끝나는 시각
     */
    // 구간 길이를 열지 않고 계산을 연다, 화면이 같은 덧셈을 따로 하면 두 곳이 갈라진다
    public static LocalDateTime resultViewingEndsAt(LocalDateTime endAt) {
        return endAt.plus(RESULT_VIEWING);
    }

    /**
     * 방을 열어 줄 수 없는 사유, 열어 주는 단계에는 없다
     */
    public Optional<AuctionRoomErrorCode> entryRejection() {
        return switch (this) {
            case NOT_OPEN -> Optional.of(AuctionRoomErrorCode.ROOM_NOT_OPEN_YET);
            case CLOSED -> Optional.of(AuctionRoomErrorCode.ROOM_ALREADY_CLOSED);
            case WAITING, LIVE, RESULT -> Optional.empty();
        };
    }

    /**
     * 구독을 열어 줄 수 없는 사유, 대기와 진행에는 없다
     */
    // 결과 구간만 입장과 갈라진다, 들어와 볼 수는 있지만 더 바뀔 값이 없어 연결은 받지 않는다
    public Optional<AuctionRoomErrorCode> streamRejection() {
        return switch (this) {
            case NOT_OPEN -> Optional.of(AuctionRoomErrorCode.ROOM_NOT_OPEN_YET);
            case RESULT -> Optional.of(AuctionRoomErrorCode.ROOM_STREAM_ENDED);
            case CLOSED -> Optional.of(AuctionRoomErrorCode.ROOM_ALREADY_CLOSED);
            case WAITING, LIVE -> Optional.empty();
        };
    }

    /**
     * 연결을 열어 두는 단계, 이 단계에서만 구독을 받고 접속자로 센다
     */
    public boolean allowsConnection() {
        return streamRejection().isEmpty();
    }

    /**
     * 개장 안내를 열어 줄 수 없는 사유, 아직 열리지 않은 단계에는 없다
     */
    // 방 입장과 반대 방향의 거절이다, 문마다의 거절 사유를 여기 모아 호출부가 같은 모양으로 읽히게 한다
    // 끝난 방은 열렸다고 답하지 않는다, 그렇게 답하면 화면이 방으로 옮겨가 한 번 더 거절받는다
    public Optional<AuctionRoomErrorCode> openingRejection() {
        return switch (this) {
            case NOT_OPEN -> Optional.empty();
            case CLOSED -> Optional.of(AuctionRoomErrorCode.ROOM_ALREADY_CLOSED);
            case WAITING, LIVE, RESULT -> Optional.of(AuctionRoomErrorCode.ROOM_ALREADY_OPEN);
        };
    }
}
