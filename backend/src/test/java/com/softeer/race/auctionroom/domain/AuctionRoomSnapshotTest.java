package com.softeer.race.auctionroom.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static com.softeer.race.auctionroom.domain.RoomPhase.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// 방 단계는 경매 상태가 아니라 시각에서 파생된다
// 경계는 포함/배제를 명시적으로 고정한다
class AuctionRoomSnapshotTest {

    private static final LocalDateTime ROOM_OPEN_AT = LocalDateTime.of(2026, 8, 3, 20, 0);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 8, 3, 20, 30);
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 8, 3, 21, 0);

    private static final long START_PRICE = 1_000_000L;

    private static AuctionRoomSnapshot endingAt(LocalDateTime endTime) {
        return new AuctionRoomSnapshot(START_PRICE, null, ROOM_OPEN_AT, START_TIME, endTime);
    }

    @DisplayName("경매방 단계와 접속자 집계 여부는 조회 시각으로 결정된다")
    @ParameterizedTest(name = "{0}")
    @MethodSource
    void phaseAt(String scenario, LocalDateTime now, RoomPhase expected, boolean presenceCounted) {
        AuctionRoomSnapshot snapshot = endingAt(END_TIME);

        RoomPhase phase = snapshot.phaseAt(now);

        assertThat(phase).isEqualTo(expected);
        assertThat(phase.isPresenceCounted()).isEqualTo(presenceCounted);
    }

    static Stream<Arguments> phaseAt() {
        return Stream.of(
                arguments("개장 직전에는 아직 열리지 않았다", ROOM_OPEN_AT.minusSeconds(1), NOT_OPEN, false),
                arguments("개장 정각부터 대기가 시작된다", ROOM_OPEN_AT, WAITING, true),
                arguments("개장과 시작 사이는 대기다", START_TIME.minusSeconds(1), WAITING, true),
                arguments("시작 정각부터 진행이다", START_TIME, LIVE, true),
                arguments("마감 직전까지 진행이 유지된다", END_TIME.minusSeconds(1), LIVE, true),
                arguments("마감 정각부터 결과 확인 구간이다", END_TIME, RESULT, true),
                arguments("마감 5분 직전까지 결과 구간이다", END_TIME.plusMinutes(5).minusSeconds(1), RESULT, true),
                arguments("마감 5분이 지나면 완전히 닫힌다", END_TIME.plusMinutes(5), CLOSED, false)
        );
    }

    @Test
    @DisplayName("소프트클로즈로 마감이 밀리면 원래 마감을 넘겨도 진행 상태다")
    void extendedDeadlineKeepsRoomLive() {
        AuctionRoomSnapshot snapshot = endingAt(END_TIME.plusMinutes(3));

        RoomPhase phase = snapshot.phaseAt(END_TIME.plusSeconds(1));

        assertThat(phase).isEqualTo(LIVE);
    }

    @Test
    @DisplayName("입찰이 없으면 현재가는 시작가다")
    void displayPriceFallsBackToStartPrice() {
        AuctionRoomSnapshot snapshot = endingAt(END_TIME);

        assertThat(snapshot.displayPrice()).isEqualTo(START_PRICE);
    }

    @Test
    @DisplayName("입찰이 있으면 현재가를 그대로 쓴다")
    void displayPriceUsesCurrentPrice() {
        AuctionRoomSnapshot snapshot =
                new AuctionRoomSnapshot(START_PRICE, 1_500_000L, ROOM_OPEN_AT, START_TIME, END_TIME);

        assertThat(snapshot.displayPrice()).isEqualTo(1_500_000L);
    }
}
