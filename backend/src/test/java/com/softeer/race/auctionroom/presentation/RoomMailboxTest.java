package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomMessage;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.ViewerCount;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.domain.RoomPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 스레드를 띄우지 않는다, 사서함이 정하는 것은 무엇을 남기고 무엇을 버리느냐이지 언제 도느냐가 아니다
class RoomMailboxTest {

    private static final long AUCTION = 1L;

    private final RoomMailbox mailbox = new RoomMailbox();

    @Test
    @DisplayName("아직 못 내보낸 사이에 새것이 오면 오래된 것을 버린다")
    void newerReplacesPending() {
        mailbox.offer(stateAt(10_000_000L));
        mailbox.offer(stateAt(12_000_000L));

        assertThat(currentPricesOf(mailbox.drainMessages())).containsExactly(12_000_000L);
    }

    @Test
    @DisplayName("이미 내보낸 것보다 낡은 것은 빈 칸에도 들어가지 않는다")
    void stalerThanSentIsDropped() {
        mailbox.offer(stateAt(12_000_000L));
        mailbox.drainMessages();

        mailbox.offer(stateAt(10_000_000L));

        assertThat(mailbox.drainMessages()).isEmpty();
    }

    // 넣은 순서를 지키는 것까지는 못 본다, 종류가 둘뿐이라 지도를 HashMap 으로 바꿔도 순서가 같게 나온다
    @Test
    @DisplayName("종류가 다르면 서로를 밀어내지 않는다")
    void differentTypesDoNotEvictEachOther() {
        mailbox.offer(new ViewerCount(AUCTION, 3, 1L));
        mailbox.offer(stateAt(10_000_000L));

        assertThat(mailbox.drainMessages())
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(ViewerCount.class, RoomState.class);
    }

    @Test
    @DisplayName("비우는 사람은 하나만 뽑히고, 그사이 새로 들어온 것이 있으면 한 바퀴 더 돈다")
    void onlyOneDrainerRunsAndRechecks() {
        mailbox.offer(stateAt(10_000_000L));

        assertThat(mailbox.claimDrain()).isTrue();
        assertThat(mailbox.claimDrain()).isFalse();

        mailbox.drainMessages();
        mailbox.offer(stateAt(12_000_000L));

        assertThat(mailbox.renewDrain()).isTrue();
        mailbox.drainMessages();
        assertThat(mailbox.renewDrain()).isFalse();
    }

    @Test
    @DisplayName("끝내기는 밀려 있던 현황 뒤에 나온다")
    void closeWaitsBehindPendingMessages() {
        mailbox.offer(stateAt(10_000_000L));
        mailbox.requestClose();

        assertThat(mailbox.claimDrain()).isTrue();
        assertThat(currentPricesOf(mailbox.drainMessages())).containsExactly(10_000_000L);
        assertThat(mailbox.drainClose()).isTrue();
        assertThat(mailbox.drainClose()).isFalse();
    }

    @Test
    @DisplayName("보낼 것이 없으면 아무도 비우러 가지 않는다")
    void emptyMailboxHasNothingToDrain() {
        assertThat(mailbox.claimDrain()).isFalse();
    }

    private static List<Long> currentPricesOf(List<RoomMessage> messages) {
        return messages.stream()
                .filter(RoomState.class::isInstance)
                .map(RoomState.class::cast)
                .map(RoomState::currentPrice)
                .toList();
    }

    private static RoomState stateAt(long currentPrice) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 12, 0);

        return new RoomState(
                AUCTION,
                RoomPhase.LIVE,
                currentPrice,
                now.plusMinutes(19),
                now,
                new BidCounts(1, 1),
                null,
                List.of());
    }
}
