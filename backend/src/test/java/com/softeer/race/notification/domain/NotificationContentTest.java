package com.softeer.race.notification.domain;

import com.softeer.race.common.domain.MaskedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("알림 문구")
class NotificationContentTest {

    private static final String MODEL = "아반떼 CN7";
    private static final long PRICE = 31_000_000L;

    @Test
    @DisplayName("상위 입찰 문구는 차량·마스킹된 입찰자·금액을 담는다")
    void outbidContainsAuctionContext() {
        NotificationContent content = NotificationContent.outbid(
                MODEL, MaskedName.mask("김민수"), 10_000_000L);

        assertThat(content.type()).isEqualTo(NotificationType.OUTBID);
        assertThat(content.message())
                .isEqualTo("아반떼 CN7 경매에서 김*수님이 10,000,000원에 입찰했습니다.");
    }

    @Test
    @DisplayName("경매 결과 문구는 수신자의 결과와 실제 성립 가격을 구분한다")
    void auctionResultDependsOnRecipient() {
        assertThat(NotificationContent.auctionWon(MODEL, PRICE).message())
                .isEqualTo("아반떼 CN7 차량을 31,000,000원에 낙찰받았습니다.");
        assertThat(NotificationContent.auctionSold(MODEL, PRICE).message())
                .isEqualTo("아반떼 CN7 차량이 31,000,000원에 낙찰되었습니다.");
        assertThat(NotificationContent.auctionEnded(MODEL, PRICE).message())
                .isEqualTo("아반떼 CN7 경매가 31,000,000원에 종료되었습니다.");
        assertThat(NotificationContent.auctionFailed(MODEL).message())
                .isEqualTo("아반떼 CN7 경매가 입찰 없이 종료되었습니다.");
    }

    @Test
    @DisplayName("거래 확정 문구는 같은 일정도 구매자에게는 인수, 판매자에게는 인도로 말한다")
    void confirmedDealDependsOnSide() {
        LocalDateTime deliveryAt = LocalDateTime.of(2026, 8, 21, 10, 0);
        String location = "부산시 해운대구 센텀중앙로 55";

        assertThat(NotificationContent.dealConfirmedForBuyer(
                MODEL, deliveryAt, location).message())
                .isEqualTo("아반떼 CN7 차량을 2026년 8월 21일 10:00에 부산시 해운대구 센텀중앙로 55에서 인수합니다.");
        assertThat(NotificationContent.dealConfirmedForSeller(
                MODEL, deliveryAt, location).message())
                .isEqualTo("아반떼 CN7 차량을 2026년 8월 21일 10:00에 부산시 해운대구 센텀중앙로 55에서 인도합니다.");
    }

    @Test
    @DisplayName("방문견적 신청 문구는 번호판까지 담아 같은 차종의 신청을 구분한다")
    void evaluationRequestedContainsPlateNumber() {
        NotificationContent content =
                NotificationContent.evaluationRequested("12가3456", MODEL);

        assertThat(content.type()).isEqualTo(NotificationType.EVAL_REQUESTED);
        assertThat(content.message())
                .isEqualTo("12가3456 아반떼 CN7 차량의 방문견적 신청이 접수되었습니다.");
    }

    @Test
    @DisplayName("기존 종류는 기본 문구를 그대로 내용으로 만들 수 있다")
    void defaultContentKeepsLegacyMessage() {
        NotificationContent content = NotificationContent.defaultOf(AUCTION_WON);

        assertThat(content.type()).isEqualTo(AUCTION_WON);
        assertThat(content.message()).isEqualTo(AUCTION_WON.defaultMessage());
    }

    @Test
    @DisplayName("동적 값의 앞뒤·연속 공백과 줄바꿈은 저장 전에 한 칸으로 정리한다")
    void normalizesWhitespaceInDynamicValues() {
        LocalDateTime deliveryAt = LocalDateTime.of(2026, 8, 21, 10, 0);

        NotificationContent content = NotificationContent.dealConfirmedForBuyer(
                "  아반떼\nCN7  ", deliveryAt, " 부산시\t  해운대구 ");

        assertThat(content.message())
                .isEqualTo("아반떼 CN7 차량을 2026년 8월 21일 10:00에 부산시 해운대구에서 인수합니다.");
    }

    @Test
    @DisplayName("금액이 들어가는 알림은 0 이하의 성립 불가능한 값을 거절한다")
    void rejectsNonPositiveMoney() {
        assertThatThrownBy(() -> NotificationContent.auctionWon(MODEL, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다 커야");
    }

    @Test
    @DisplayName("DB에 저장할 수 없는 빈 문구와 1000자 초과 문구는 만들지 못한다")
    void rejectsInvalidStoredMessage() {
        assertThatThrownBy(() -> new NotificationContent(AUCTION_WON, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationContent(AUCTION_WON, "가".repeat(1_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
