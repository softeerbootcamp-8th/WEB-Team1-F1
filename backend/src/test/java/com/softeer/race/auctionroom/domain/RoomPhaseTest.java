package com.softeer.race.auctionroom.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_ALREADY_CLOSED;
import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_ALREADY_OPEN;
import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_NOT_OPEN_YET;
import static org.assertj.core.api.Assertions.assertThat;

// 단계 이름은 응답에 그대로 실려 FE 화면 분기의 기준이 된다
class RoomPhaseTest {

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
}
