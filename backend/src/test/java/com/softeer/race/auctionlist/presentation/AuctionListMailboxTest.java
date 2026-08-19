package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.vehicle.domain.Manufacturer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("목록 사서함 테스트")
class AuctionListMailboxTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 20, 0);
    private static final long START_PRICE = 10_000_000L;

    private final AuctionListMailbox mailbox = new AuctionListMailbox();

    @Test
    @DisplayName("같은 경매 카드가 밀리면 최신 한 건만 나간다")
    void foldsCardsOfSameAuction() {
        mailbox.offer(card(1L, START_PRICE));
        mailbox.offer(card(1L, START_PRICE + 1_000_000L));

        assertThat(prices(mailbox.drainMessages())).containsExactly(START_PRICE + 1_000_000L);
    }

    @Test
    @DisplayName("다른 경매 카드는 서로를 덮지 않는다")
    void keepsCardsOfDifferentAuctions() {
        mailbox.offer(card(1L, START_PRICE));
        mailbox.offer(card(2L, START_PRICE));

        assertThat(mailbox.drainMessages()).extracting(AuctionListMessage::auctionId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("현재가가 더 낮은 카드는 이미 내보낸 것을 덮지 않는다")
    void ignoresStaleCard() {
        mailbox.offer(card(1L, START_PRICE + 1_000_000L));
        mailbox.drainMessages();

        mailbox.offer(card(1L, START_PRICE));

        assertThat(mailbox.drainMessages()).isEmpty();
    }

    @Test
    @DisplayName("현재가가 더 낮은 카드는 아직 안 나간 것도 덮지 않는다")
    void ignoresStaleCardAgainstPending() {
        mailbox.offer(card(1L, START_PRICE + 1_000_000L));
        mailbox.offer(card(1L, START_PRICE));

        assertThat(prices(mailbox.drainMessages())).containsExactly(START_PRICE + 1_000_000L);
    }

    @Test
    @DisplayName("넣은 순서대로 나간다")
    void keepsOfferedOrder() {
        mailbox.offer(card(3L, START_PRICE));
        mailbox.offer(card(1L, START_PRICE));
        mailbox.offer(card(2L, START_PRICE));

        assertThat(mailbox.drainMessages()).extracting(AuctionListMessage::auctionId).containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("내보낼 것이 없으면 일꾼을 잡지 않는다")
    void doesNotClaimWhenEmpty() {
        assertThat(mailbox.claimDrain()).isFalse();
    }

    @Test
    @DisplayName("구독 하나에 일꾼이 둘 붙지 않는다")
    void claimsOnlyOneWorker() {
        mailbox.offer(card(1L, START_PRICE));

        assertThat(mailbox.claimDrain()).isTrue();
        assertThat(mailbox.claimDrain()).isFalse();
    }

    @Test
    @DisplayName("일꾼이 도는 중에 들어온 것은 다음 바퀴가 가져간다")
    void nextLapTakesWhatArrivedWhileDraining() {
        mailbox.offer(card(1L, START_PRICE));
        mailbox.claimDrain();
        mailbox.drainMessages();

        mailbox.offer(card(2L, START_PRICE));

        assertThat(mailbox.renewDrain()).isTrue();
        assertThat(mailbox.drainMessages()).extracting(AuctionListMessage::auctionId).containsExactly(2L);
        assertThat(mailbox.renewDrain()).isFalse();
    }

    @Test
    @DisplayName("연결 확인만 밀려 있어도 일꾼이 붙어 나간다")
    void pingAloneIsDelivered() {
        mailbox.requestPing();

        assertThat(mailbox.claimDrain()).isTrue();
        assertThat(mailbox.drainPing()).isTrue();
        assertThat(mailbox.drainPing()).isFalse();
    }

    @Test
    @DisplayName("끝내기는 밀린 카드를 다 내보낸 뒤에 나간다")
    void closeWaitsForPendingCards() {
        mailbox.offer(card(1L, START_PRICE));
        mailbox.requestClose();

        assertThat(mailbox.drainMessages()).hasSize(1);
        assertThat(mailbox.drainClose()).isTrue();
        assertThat(mailbox.drainClose()).isFalse();
    }

    private static List<Long> prices(List<AuctionListMessage> messages) {
        return messages.stream()
                .map(message -> ((CardMessage) message).card().currentPrice())
                .toList();
    }

    private static CardMessage card(long auctionId, long currentPrice) {
        return new CardMessage(new AuctionCardInfo(auctionId, RoomPhase.LIVE, "https://cdn.race/1.jpg",
                Manufacturer.HYUNDAI, "아반떼", 2020, 30_000, List.of(), START_PRICE, currentPrice,
                NOW.minusHours(1), NOW.minusMinutes(30), NOW.plusMinutes(10), 0));
    }
}
