package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auctionpost.domain.AuctionPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_ALREADY_CLOSED;
import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_ALREADY_OPEN;
import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_NOT_OPEN_YET;
import static com.softeer.race.auctionroom.domain.RoomPhase.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// 단계 이름은 응답에 그대로 실려 FE 화면 분기의 기준이 된다
// 방 단계는 경매 상태가 아니라 시각에서 파생되고, 경계는 포함/배제를 명시적으로 고정한다
class RoomPhaseTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 27, 15, 30);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 7, 27, 16, 30);

    // schedule()이 계산하는 값이다, 개장은 시작 30분 전이고 마감은 시작 20분 뒤다
    private static final LocalDateTime ROOM_OPEN_AT = LocalDateTime.of(2026, 7, 27, 16, 0);
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 7, 27, 16, 50);

    @Test
    @DisplayName("방 단계 이름과 순서는 화면 계약이라 바뀌면 안 된다")
    void phaseNamesArePartOfTheContract() {
        assertThat(RoomPhase.values())
                .extracting(Enum::name)
                .containsExactly("NOT_OPEN", "WAITING", "LIVE", "RESULT", "CLOSED");
    }

    @Test
    @DisplayName("입장 거절 사유는 아직 열리지 않음과 이미 끝남으로 갈린다")
    void entryRejectionSplitsByReason() {
        assertThat(RoomPhase.NOT_OPEN.entryRejection()).contains(ROOM_NOT_OPEN_YET);
        assertThat(RoomPhase.CLOSED.entryRejection()).contains(ROOM_ALREADY_CLOSED);

        assertThat(RoomPhase.WAITING.entryRejection()).isEmpty();
        assertThat(RoomPhase.LIVE.entryRejection()).isEmpty();
        assertThat(RoomPhase.RESULT.entryRejection()).isEmpty();
    }

    @Test
    @DisplayName("거절 사유가 없는 단계가 곧 접속자로 세는 단계다")
    void connectionFollowsRejection() {
        assertThat(Arrays.stream(RoomPhase.values()).filter(RoomPhase::allowsConnection))
                .containsExactly(RoomPhase.WAITING, RoomPhase.LIVE, RoomPhase.RESULT);
    }

    @Test
    @DisplayName("개장 안내를 여는 단계는 아직 열리지 않은 방 하나뿐이고, 나머지는 이미 열렸다고 거절한다")
    void openingOpensOnlyBeforeOpen() {
        assertThat(RoomPhase.NOT_OPEN.openingRejection()).isEmpty();

        assertThat(Arrays.stream(RoomPhase.values())
                .filter(phase -> phase.openingRejection().isPresent()))
                .containsExactly(RoomPhase.WAITING, RoomPhase.LIVE, RoomPhase.RESULT, RoomPhase.CLOSED);

        assertThat(RoomPhase.CLOSED.openingRejection()).contains(ROOM_ALREADY_OPEN);
    }

    @DisplayName("경매방 단계는 조회 시각으로 결정된다")
    @ParameterizedTest(name = "{0}")
    @MethodSource
    void at(String scenario, LocalDateTime now, RoomPhase expected) {
        assertThat(RoomPhase.at(now, ROOM_OPEN_AT, START_TIME, END_TIME)).isEqualTo(expected);
    }

    static Stream<Arguments> at() {
        return Stream.of(
                arguments("개장 직전에는 아직 열리지 않았다", ROOM_OPEN_AT.minusSeconds(1), NOT_OPEN),
                arguments("개장 정각부터 대기가 시작된다", ROOM_OPEN_AT, WAITING),
                arguments("개장과 시작 사이는 대기다", START_TIME.minusSeconds(1), WAITING),
                arguments("시작 정각부터 진행이다", START_TIME, LIVE),
                arguments("마감 직전까지 진행이 유지된다", END_TIME.minusSeconds(1), LIVE),
                arguments("마감 정각부터 결과 확인 구간이다", END_TIME, RESULT),
                arguments("마감 5분 직전까지 결과 구간이다", END_TIME.plusMinutes(5).minusSeconds(1), RESULT),
                arguments("마감 5분이 지나면 완전히 닫힌다", END_TIME.plusMinutes(5), CLOSED)
        );
    }

    @Test
    @DisplayName("소프트클로즈로 마감이 밀리면 원래 마감을 넘겨도 진행 상태다")
    void extendedDeadlineKeepsRoomLive() {
        RoomPhase phase = RoomPhase.at(
                END_TIME.plusSeconds(1), ROOM_OPEN_AT, START_TIME, END_TIME.plusMinutes(3));

        assertThat(phase).isEqualTo(LIVE);
    }

    // 입찰 판정과 단계 판정이 같은 구간을 봐야 한다, 갈라지면 화면이 "진행 중"인 방이 입찰을 거절한다
    @DisplayName("입찰을 받는 시각은 반드시 진행 단계다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "2026-07-27T16:00:00",  // 개장 정각, 아직 대기
            "2026-07-27T16:29:59",
            "2026-07-27T16:30:00",  // 시작 정각, 포함
            "2026-07-27T16:49:59",
            "2026-07-27T16:50:00"   // 마감 정각, 제외
    })
    void biddableExactlyWhenLive(LocalDateTime now) {
        Auction auction = Auction.schedule(AuctionPost.create(null, null, PUBLISHED_AT), 10_000_000L, START_TIME);

        RoomPhase phase = RoomPhase.at(
                now, auction.getRoomOpenAt(), auction.getStartTime(), auction.getCurrentEndTime());

        assertThat(auction.isBiddableAt(now)).isEqualTo(phase == LIVE);
    }
}
