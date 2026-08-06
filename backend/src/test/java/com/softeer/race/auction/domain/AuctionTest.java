package com.softeer.race.auction.domain;

import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 27, 15, 30);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 7, 27, 16, 30);
    // schedule()이 계산하는 마감 = START_TIME + 20분
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 7, 27, 16, 50);
    // schedule()이 계산하는 방 개설 = START_TIME - 30분
    private static final LocalDateTime ROOM_OPEN_AT = LocalDateTime.of(2026, 7, 27, 16, 0);

    private static final User ALICE = User.create(
            "alice", "alice@race.com", "encoded", "김앨리스", "01011112222", "서울 강남구", Role.GENERAL);

    @Test
    @DisplayName("경매를 예약하면 방 개설은 시작 30분 전, 마감은 시작 20분 후가 된다.")
    void schedule_시각_계산() {
        Auction auction = Auction.schedule(post(), 10_000_000L, START_TIME);

        assertThat(auction.getRoomOpenAt()).isEqualTo(LocalDateTime.of(2026, 7, 27, 16, 0));
        assertThat(auction.getStartTime()).isEqualTo(START_TIME);
        assertThat(auction.getCurrentEndTime()).isEqualTo(LocalDateTime.of(2026, 7, 27, 16, 50));
    }

    @Test
    @DisplayName("예약된 경매는 SCHEDULED 상태이며 입찰 이력이 없다.")
    void schedule_초기_상태() {
        Auction auction = Auction.schedule(post(), 10_000_000L, START_TIME);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        assertThat(auction.getExtensionCount()).isZero();
        assertThat(auction.getCurrentPrice()).isNull();
        assertThat(auction.getWinner()).isNull();
        assertThat(auction.getStartPrice()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("시작 시각이 발행 시각으로부터 정확히 1시간 뒤면 예약할 수 있다")
    void schedule_시작시각_정확히_1시간_통과() {
        Auction auction = Auction.schedule(post(), 25_000_000L, START_TIME);

        assertThat(auction.getStartTime()).isEqualTo(START_TIME);
    }

    @Test
    @DisplayName("시작 시각이 발행 시간보다 1시간 미만이면 예약할 수 없다.")
    void schedule_시작시각_1시간미만_거부() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 27, 16, 29);

        assertThatThrownBy(() -> Auction.schedule(post(), 25_000_000L, startTime))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.INVALID_START_AT);
    }

    // 기대값의 30초는 일부러 하드코딩한다
    // 상수를 실수로 바꾸면 테스트가 깨져야 하므로 SOFT_CLOSE_WINDOW를 노출해 참조하지 않는다
    @DisplayName("잔여 시간이 30초 이하인 입찰만 마감을 입찰 시각 + 30초로 다시 채운다")
    @ParameterizedTest(name = "잔여 {0}초 → 연장 {1}")
    @CsvSource({
            "31, false", // 임계값 밖, 1초 차이로 갈리는 지점을 고정한다
            "30, true",  // 경계 포함 — FR-3.3.1이 "이하"다
            "29, true",
            "1,  true"   // 마감 직전
    })
    void extendsOnlyInsideWindow(long remainingSeconds, boolean extended) {
        Auction auction = scheduled();
        LocalDateTime bidAt = END_TIME.minusSeconds(remainingSeconds);

        auction.extendIfClosingSoon(bidAt);

        assertThat(auction.getCurrentEndTime())
                .isEqualTo(extended ? bidAt.plusSeconds(30) : END_TIME);
        assertThat(auction.getExtensionCount()).isEqualTo(extended ? 1 : 0);
    }

    @Test
    @DisplayName("연장이 반복돼도 마감은 마지막 입찰 시각 기준으로만 정해진다")
    void doesNotAccumulateExtensions() {
        Auction auction = scheduled();
        LocalDateTime firstBid = END_TIME.minusSeconds(10);   // 잔여 10초
        LocalDateTime secondBid = firstBid.plusSeconds(25);   // 새 마감 기준 잔여 5초

        auction.extendIfClosingSoon(firstBid);
        auction.extendIfClosingSoon(secondBid);

        // 누적 가산이면 원래 마감 + 60초가 된다, 이 단정이 누적 여부를 가리는 유일한 검증이다
        assertThat(auction.getCurrentEndTime()).isEqualTo(secondBid.plusSeconds(30));
        assertThat(auction.getExtensionCount()).isEqualTo(2);
    }

    @DisplayName("마감 시각에 도달한 입찰은 연장을 만들지 못한다")
    @ParameterizedTest(name = "마감 {0}초 후 입찰")
    @ValueSource(longs = {0, 1}) // 0 = 마감 정각(진행이 끝난 첫 순간), 1 = 마감 이후
    void rejectsBidAtOrAfterDeadline(long secondsAfterEnd) {
        Auction auction = scheduled();

        assertThatThrownBy(() -> auction.extendIfClosingSoon(END_TIME.plusSeconds(secondsAfterEnd)))
                .isInstanceOf(IllegalStateException.class);
    }

    // 저장된 status가 아니라 서버 시각으로 판정한다, 경계는 phaseAt의 LIVE 구간과 같아야 한다
    // 시작 정각은 포함하고 마감 정각은 제외한다, 화면이 "진행 중"으로 보여준 경매만 입찰을 받는다
    @DisplayName("입찰은 시작 시각부터 마감 시각 직전까지만 받는다")
    @ParameterizedTest(name = "{0} → 입찰 가능 {1}")
    @CsvSource({
            "2026-07-27T16:00:00, false", // 방은 열렸지만 아직 대기 구간이다
            "2026-07-27T16:29:59, false",
            "2026-07-27T16:30:00, true",  // 시작 정각, 포함
            "2026-07-27T16:49:59, true",
            "2026-07-27T16:50:00, false"  // 마감 정각, 제외
    })
    void isBiddableOnlyBetweenStartAndEnd(LocalDateTime now, boolean biddable) {
        assertThat(scheduled().isBiddableAt(now)).isEqualTo(biddable);
    }

    @Test
    @DisplayName("입찰이 성립하면 현재가와 갱신 시각만 바뀐다")
    void acceptBidUpdatesPriceOnly() {
        Auction auction = scheduled();
        LocalDateTime bidAt = START_TIME.plusMinutes(5);

        auction.acceptBid(12_000_000L, bidAt);

        assertThat(auction.getCurrentPrice()).isEqualTo(12_000_000L);
        assertThat(auction.getPriceUpdatedAt()).isEqualTo(bidAt);
        // 낙찰 확정과 상태 전환은 마감 뒤의 일이라 최고가 갱신이 건드리지 않는다
        assertThat(auction.getWinner()).isNull();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
    }

    // 위 extendsOnlyInsideWindow와 겹쳐 보이지만 검증 대상이 다르다
    // 저건 연장 규칙 자체를, 이건 acceptBid가 그 규칙에 판정을 넘기는지를 본다
    // 위임을 빼먹으면 기존 연장 테스트는 전부 통과하고 이 케이스만 깨진다
    @DisplayName("입찰 성립은 마감 연장 판정까지 함께 처리한다")
    @ParameterizedTest(name = "잔여 {0}초 → 연장 {1}")
    @CsvSource({
            "31, false",
            "30, true"
    })
    void acceptBidDelegatesExtension(long remainingSeconds, boolean extended) {
        Auction auction = scheduled();
        LocalDateTime bidAt = END_TIME.minusSeconds(remainingSeconds);

        auction.acceptBid(12_000_000L, bidAt);

        assertThat(auction.getCurrentEndTime())
                .isEqualTo(extended ? bidAt.plusSeconds(30) : END_TIME);
        assertThat(auction.getExtensionCount()).isEqualTo(extended ? 1 : 0);
    }

    // acceptBid의 사전조건, isBiddableAt이 통과시킨 시각만 들어와야 한다
    // 두 판정이 다른 시각을 봤다는 뜻이라 사용자가 재시도할 수 있는 실패가 아니다
    @DisplayName("마감을 지난 시각으로 입찰을 반영하면 서버 결함으로 중단한다")
    @Test
    void acceptBidAfterDeadlineFails() {
        assertThatThrownBy(() -> scheduled().acceptBid(12_000_000L, END_TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================= 종료·낙찰 확정 =================

    @Test
    @DisplayName("마감에 도달한 경매를 종료하면 최고 입찰자가 낙찰자로 확정된다")
    void close_낙찰() {
        Auction auction = inProgress();
        auction.acceptBid(12_000_000L, START_TIME.plusMinutes(1));

        auction.close(ALICE, END_TIME);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(auction.getWinner()).isEqualTo(ALICE);
        // 낙찰가는 종료가 새로 정하는 값이 아니라 마지막 입찰이 남긴 현재가 그대로다
        assertThat(auction.getCurrentPrice()).isEqualTo(12_000_000L);
    }

    @Test
    @DisplayName("입찰이 한 건도 없이 마감되면 낙찰자 없이 유찰된다")
    void close_유찰() {
        Auction auction = inProgress();

        auction.close(null, END_TIME);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(auction.getWinner()).isNull();
    }

    // 마감 정각은 입찰이 안 되고 종료는 된다
    // 두 판정 중 한쪽만 부등호를 바꾸면 입찰도 종료도 안 되는 시각이 생기거나 둘 다 되는 시각이 생긴다
    @DisplayName("마감 정각부터 종료할 수 있고, 그 순간부터 입찰은 성립하지 않는다")
    @ParameterizedTest(name = "마감 {0}초 전 → 종료 가능 {1}")
    @CsvSource({
            "1,  false",
            "0,  true",
            "-1, true"
    })
    void close_경계(long secondsBeforeEnd, boolean closable) {
        Auction auction = inProgress();
        LocalDateTime now = END_TIME.minusSeconds(secondsBeforeEnd);

        assertThat(auction.isClosableAt(now)).isEqualTo(closable);
        assertThat(auction.isBiddableAt(now)).isEqualTo(!closable);
    }

    // close 의 사전조건, 호출자가 잠금 안에서 isClosableAt 으로 걸러야 한다
    // 여기 도달했다면 판정과 확정이 다른 시각을 봤다는 뜻이라 사용자가 재시도할 수 있는 실패가 아니다
    @Test
    @DisplayName("마감 전 경매를 종료하려 하면 서버 결함으로 중단한다")
    void close_마감전_거부() {
        assertThatThrownBy(() -> inProgress().close(ALICE, END_TIME.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 종료된 경매를 다시 종료하려 하면 서버 결함으로 중단한다")
    void close_중복_거부() {
        Auction auction = inProgress();
        auction.close(ALICE, END_TIME);

        assertThatThrownBy(() -> auction.close(ALICE, END_TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("유찰된 경매도 다시 종료할 수 없다")
    void close_유찰후_중복_거부() {
        Auction auction = inProgress();
        auction.close(null, END_TIME);

        assertThatThrownBy(() -> auction.close(ALICE, END_TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    // 소프트클로즈(#44)와 종료(#63)가 만나는 지점, 이 테스트가 둘의 계약을 묶는다
    // 종료 후보를 뽑은 뒤 잠금을 얻기까지 사이에 마감이 밀릴 수 있어, 잠금 후 재판정이 필요한 근거가 된다
    @Test
    @DisplayName("마감이 연장되면 원래 마감 시각으로는 종료할 수 없고 연장된 마감을 넘겨야 종료된다")
    void close_연장된_경매() {
        Auction auction = inProgress();
        LocalDateTime lastBid = END_TIME.minusSeconds(10);
        auction.acceptBid(12_000_000L, lastBid);

        assertThat(auction.isClosableAt(END_TIME)).isFalse();
        assertThat(auction.isClosableAt(lastBid.plusSeconds(30))).isTrue();
    }

    // ================= 시작 전이 =================

    @Test
    @DisplayName("시작 시각이 지나면 진행 중으로 올린다")
    void start_전이() {
        Auction auction = scheduled();

        auction.start(START_TIME);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @DisplayName("시작 정각부터 전이할 수 있고 그 전에는 할 수 없다")
    @ParameterizedTest(name = "시작 {0}초 전 → 전이 가능 {1}")
    @CsvSource({
            "1,  false",
            "0,  true",
            "-1, true"
    })
    void start_경계(long secondsBeforeStart, boolean startable) {
        Auction auction = scheduled();

        assertThat(auction.isStartableAt(START_TIME.minusSeconds(secondsBeforeStart))).isEqualTo(startable);
    }

    @Test
    @DisplayName("시작 전 경매를 올리려 하면 서버 결함으로 중단한다")
    void start_시작전_거부() {
        assertThatThrownBy(() -> scheduled().start(START_TIME.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 진행 중인 경매를 다시 올리려 하면 서버 결함으로 중단한다")
    void start_중복_거부() {
        Auction auction = scheduled();
        auction.start(START_TIME);

        assertThatThrownBy(() -> auction.start(START_TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 끝난 경매는 다시 진행 중으로 돌아가지 않는다")
    void start_종료후_거부() {
        Auction auction = inProgress();
        auction.close(null, END_TIME);

        assertThatThrownBy(() -> auction.start(END_TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    // 스케줄러가 오래 멈췄다 재개하면 시작과 마감이 함께 지나 있다.
    // 그때도 올려야 같은 주기의 종료 처리가 이어받는다, 마감 조건을 걸면 그 경매는 영영 안 닫힌다.
    @Test
    @DisplayName("마감까지 지난 예약 경매도 진행 중으로 올릴 수 있다")
    void start_마감까지_지난_경우() {
        Auction auction = scheduled();
        LocalDateTime afterEnd = END_TIME.plusMinutes(10);

        auction.start(afterEnd);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.IN_PROGRESS);
        // 올린 직후 바로 종료 대상이 된다
        assertThat(auction.isClosableAt(afterEnd)).isTrue();
    }

    // 입찰 가능 여부는 시각만 보고 정한다는 계약.
    // 전이가 늦게 돌아도 사용자가 입찰할 수 있는지는 달라지면 안 된다.
    // 종료 판정은 다르다 — 예약 → 진행 중 → 종료 순서를 도메인이 지키려고 일부러 status 를 본다.
    @DisplayName("진행 중으로 올려도 입찰 가능 여부는 달라지지 않는다")
    @ParameterizedTest(name = "시작 {0}분 후")
    @ValueSource(longs = {0, 10, 20, 30})
    void start_입찰판정에_영향없음(long minutesAfterStart) {
        LocalDateTime at = START_TIME.plusMinutes(minutesAfterStart);
        Auction auction = scheduled();

        boolean biddableBefore = auction.isBiddableAt(at);

        auction.start(START_TIME);

        assertThat(auction.isBiddableAt(at)).isEqualTo(biddableBefore);
    }

    // 반대로 종료 판정은 전이에 달려 있다, 예약 상태로는 닫히지 않는다
    @Test
    @DisplayName("예약 상태로는 마감이 지나도 종료 대상이 아니다")
    void 예약상태는_종료대상_아님() {
        Auction auction = scheduled();
        LocalDateTime afterEnd = END_TIME.plusMinutes(10);

        assertThat(auction.isClosableAt(afterEnd)).isFalse();

        auction.start(afterEnd);

        assertThat(auction.isClosableAt(afterEnd)).isTrue();
    }

    // ================= 수정 (방 개설 전) =================

    @DisplayName("방 개설 정각부터는 수정할 수 없고, 그 전에는 할 수 있다")
    @ParameterizedTest(name = "방 개설 {0}초 전 → 수정 가능 {1}")
    @CsvSource({
            "1,  true",
            "0,  false",
            "-1, false"
    })
    void isEditableAt_경계(long secondsBeforeRoomOpen, boolean editable) {
        Auction auction = scheduled();
        LocalDateTime now = ROOM_OPEN_AT.minusSeconds(secondsBeforeRoomOpen);

        assertThat(auction.isEditableAt(now)).isEqualTo(editable);
    }

    @Test
    @DisplayName("방 개설 전에 수정하면 시작가·시작 시각과 그 파생값(방 개설·마감 시각)이 함께 갱신된다")
    void updateBeforeRoomOpens_갱신() {
        Auction auction = scheduled();
        LocalDateTime now = ROOM_OPEN_AT.minusMinutes(10);
        LocalDateTime newStartTime = now.plusHours(2);

        auction.updateBeforeRoomOpens(25_000_000L, newStartTime, now);

        assertThat(auction.getStartPrice()).isEqualTo(25_000_000L);
        assertThat(auction.getStartTime()).isEqualTo(newStartTime);
        assertThat(auction.getRoomOpenAt()).isEqualTo(newStartTime.minusMinutes(30));
        assertThat(auction.getCurrentEndTime()).isEqualTo(newStartTime.plusMinutes(20));
    }

    @Test
    @DisplayName("방이 열린 뒤에는 수정할 수 없다")
    void updateBeforeRoomOpens_방개설후_거부() {
        Auction auction = scheduled();

        assertThatThrownBy(() -> auction.updateBeforeRoomOpens(25_000_000L, ROOM_OPEN_AT.plusHours(2), ROOM_OPEN_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.AUCTION_ROOM_ALREADY_OPEN);
    }

    // 리드타임 회귀 테스트: 발행 시각이 아니라 "지금(수정 요청 시각)" 기준으로 검사해야 한다.
    // publishedAt 기준으로 되돌아가면, 발행 후 시간이 오래 지난 뒤에는 리드타임 없이
    // 임박한 시각으로도 수정이 통과해버린다 (실제로 발견됐던 버그).
    @Test
    @DisplayName("발행 후 오래 지났어도 수정 시각의 리드타임은 지금 기준으로 다시 검사한다")
    void updateBeforeRoomOpens_리드타임은_지금기준() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        LocalDateTime originalStartTime = LocalDateTime.of(2026, 8, 1, 10, 0);
        Auction auction = Auction.schedule(
                AuctionPost.create(null, publishedAt), 10_000_000L, originalStartTime);

        // roomOpenAt(8/1 09:30)보다 한참 전이라 편집 가능한 시각이지만,
        // publishedAt(7/20 12:00) + 1시간은 이미 훌쩍 지난 뒤다
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);
        LocalDateTime tooSoonStartTime = now.plusMinutes(10); // 지금부터 10분 뒤, 리드타임 1시간 미달

        assertThatThrownBy(() -> auction.updateBeforeRoomOpens(10_000_000L, tooSoonStartTime, now))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.INVALID_START_AT);
    }

    private Auction scheduled() {
        return Auction.schedule(post(), 10_000_000L, START_TIME);
    }

    // 종료는 진행 중인 경매만 대상으로 한다, 실제로도 시작 전이를 거친 경매만 닫힌다
    private Auction inProgress() {
        Auction auction = scheduled();
        auction.start(START_TIME);

        return auction;
    }

    private AuctionPost post() {
        return AuctionPost.create(null, PUBLISHED_AT);
    }
}
