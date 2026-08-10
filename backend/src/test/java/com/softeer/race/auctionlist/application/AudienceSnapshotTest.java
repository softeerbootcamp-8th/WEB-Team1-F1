package com.softeer.race.auctionlist.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

// 시청자 수는 1초마다 훑지만 대부분은 그대로다, 안 바뀐 것까지 보내면 조용한 방에도 매 초 방송이 나간다
class AudienceSnapshotTest {

    private static final long AUCTION = 1L;
    private static final long OTHER_AUCTION = 2L;

    private final AudienceSnapshot snapshot = new AudienceSnapshot();

    @Test
    @DisplayName("처음 본 경매는 바뀐 것으로 나온다")
    void firstSeenAuctionIsAChange() {
        Map<Long, Integer> changed = snapshot.advanceTo(Map.of(AUCTION, 3));

        assertThat(changed).containsExactly(entry(AUCTION, 3));
    }

    @Test
    @DisplayName("같은 수가 다시 오면 나오지 않는다")
    void unchangedCountIsNotReported() {
        snapshot.advanceTo(Map.of(AUCTION, 3));

        Map<Long, Integer> changed = snapshot.advanceTo(Map.of(AUCTION, 3));

        assertThat(changed).isEmpty();
    }

    @Test
    @DisplayName("수가 달라진 경매만 나온다")
    void onlyChangedAuctionsAreReported() {
        snapshot.advanceTo(Map.of(AUCTION, 3, OTHER_AUCTION, 5));

        Map<Long, Integer> changed = snapshot.advanceTo(Map.of(AUCTION, 4, OTHER_AUCTION, 5));

        assertThat(changed).containsExactly(entry(AUCTION, 4));
    }

    @Test
    @DisplayName("사라진 경매는 0으로 한 번만 나온다")
    void vanishedAuctionIsReportedZeroOnce() {
        snapshot.advanceTo(Map.of(AUCTION, 3));

        Map<Long, Integer> emptied = snapshot.advanceTo(Map.of());
        Map<Long, Integer> stillEmpty = snapshot.advanceTo(Map.of());

        assertThat(emptied).containsExactly(entry(AUCTION, 0));
        assertThat(stillEmpty).isEmpty();
    }
}
