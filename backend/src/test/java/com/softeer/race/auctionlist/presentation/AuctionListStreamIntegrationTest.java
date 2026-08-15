package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auction.application.AuctionProgressScheduler;
import com.softeer.race.auctionlist.application.AuctionListChannel;
import com.softeer.race.auctionlist.application.AuctionListStreamService;
import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.bid.domain.BidAccepted;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 목록 변화 구독을 컨트롤러에서 채널까지
 * <p>
 * 단위테스트로는 볼 수 없는 것만 여기서 고정한다. 응답이 끝나지 않고 열린 채 남는지,
 * 미디어타입이 맞는지, 세션 없이도 열리는지, 그리고 커밋된 입찰이 열려 있는 연결로
 * 흘러 들어가는지다. 넷 다 객체를 돌려받아서는 알 수 없다.
 * <p>
 * 보는 사람이 없을 때 조회를 건너뛰는지는 여기서 보지 않는다. 쿼리 수는 클라이언트가 관찰할 수
 * 있는 동작이 아니라 구현 속성이고 show-sql 로 눈으로 확인한다.
 */
@DisplayName("경매 목록 스트림 통합 테스트")
@Sql("/sql/bid-increment-bands.sql")
class AuctionListStreamIntegrationTest extends IntegrationTestSupport {

    // 러너 시간대(UTC)와 로컬(KST)에서 결과가 갈리지 않도록 실제 시각을 쓰지 않는다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    private static final long START_PRICE = 10_000_000L;

    @Autowired
    private AuctionListChannel auctionListChannel;

    @Autowired
    private AuctionListStreamService auctionListStreamService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AuctionProgressScheduler scheduler;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp(@Autowired PlatformTransactionManager transactionManager) {
        transactionTemplate = new TransactionTemplate(transactionManager);
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 로그인 없이 구독 -> 연결이 열린 채 남고 아무것도 흐르지 않는다")
    void subscribingWithoutLoginOpensStream() throws Exception {
        // when : 세션 쿠키를 싣지 않는다
        MvcResult opened = subscribe()
                .andExpectAll(
                        // then 1 : 목록 조회가 비로그인이라 미는 통로도 로그인을 요구하지 않는다
                        status().isOk(),
                        // then 2 : 응답이 끝나지 않고 비동기로 열린 채 남는다
                        request().asyncStarted(),
                        content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        // then 3 : 서버가 그 사람의 탭과 페이지를 몰라 첫 현황이 정의되지 않는다.
        // 헤더를 밀어내는 주석 한 줄은 실리므로 데이터가 없는 것으로 단정한다
        assertThat(body(opened)).doesNotContain("data:");

        // then 4 : 명부에 실제로 들어갔다, 안 들어가면 방송이 이 연결을 못 찾는다
        assertThat(auctionListChannel.hasSubscribers()).isTrue();
    }

    @Test
    @DisplayName("시나리오 2 : 구독을 열어 둔 채 남이 입찰 -> 그 경매 카드가 현재가와 마감을 싣고 흘러온다")
    void acceptedBidStreamsCard() throws Exception {
        // given : 진행 중인 경매와 그것을 보고 있는 사람. 키워드가 붙은 차량이다
        long auctionId = liveAuction();
        tagKeyword(auctionId, "ACCIDENT_FREE");
        MvcResult opened = subscribe().andExpect(request().asyncStarted()).andReturn();

        // when : 다른 사람이 입찰한다
        long amount = START_PRICE + 1_000_000L;
        bid(auctionId, amount).andExpect(status().isCreated());

        // then 1 : 화면이 알 길 없는 값이 카드에 실려 온다. 마감은 연장 여부까지 담아야 해서 함께 온다
        assertThat(body(opened))
                .contains("event:card")
                .contains("\"auctionId\":" + auctionId)
                .contains("\"currentPrice\":" + amount)
                .contains("\"endAt\"");

        // then 2 : 조회와 같은 모양이어야 한다, 비워 보내면 화면이 들고 있던 키워드가 방송에 지워진다
        assertThat(body(opened)).contains("\"keywords\":[\"ACCIDENT_FREE\"]");
    }

    @Test
    @DisplayName("시나리오 3 : 거절된 입찰 -> 카드가 나가지 않는다")
    void rejectedBidStreamsNothing() throws Exception {
        // given : 시작가보다 낮은 금액이라 거절된다
        long auctionId = liveAuction();
        MvcResult opened = subscribe().andExpect(request().asyncStarted()).andReturn();

        // when : 금액 규칙 위반은 문법이 아니라 도메인 거절이라 409 다
        bid(auctionId, START_PRICE - 1L)
                .andExpectAll(status().isConflict(), jsonPath("$.code").value("BID_AMOUNT_TOO_LOW"));

        // then : 성립하지 않은 입찰은 방송의 근거가 아니다.
        // 이 경로는 이벤트가 아예 발행되지 않아 리스너의 phase 와 무관하다, 그쪽은 시나리오 4 가 본다
        assertThat(body(opened)).doesNotContain("data:");
    }

    @Test
    @DisplayName("시나리오 4 : 이벤트가 발행된 트랜잭션이 롤백 -> 카드가 나가지 않는다")
    void rolledBackTransactionStreamsNothing() throws Exception {
        // given : 발행까지 갔다가 뒤집히는 트랜잭션이라야 리스너의 phase 가 드러난다.
        // 거절된 입찰로는 재현되지 않는다, 그쪽은 발행 전에 끊긴다
        long auctionId = liveAuction();
        MvcResult opened = subscribe().andExpect(request().asyncStarted()).andReturn();

        // when
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new BidAccepted(auctionId));
            status.setRollbackOnly();

            return null;
        });

        // then : AFTER_COMPLETION 이면 확정되지 않은 카드가 화면에 남는다
        assertThat(body(opened)).doesNotContain("data:");
    }

    @Test
    @DisplayName("시나리오 5 : 경매글이 내려간 뒤의 방송 -> 조용히 넘어간다")
    void deletedPostStreamsNothing() throws Exception {
        // given : 구독이 열려 있는 사이에 경매글이 내려갔다
        long auctionId = liveAuction();
        MvcResult opened = subscribe().andExpect(request().asyncStarted()).andReturn();
        deletePostOf(auctionId);

        // when : 이벤트 경로로는 재현할 수 없어 방송을 직접 부른다
        auctionListStreamService.broadcastCard(auctionId);

        // then : 조회에서 빠진 경매가 방송으로 들어오면 목록에 없던 카드가 화면에 생긴다
        assertThat(body(opened)).doesNotContain("data:");
    }

    @Test
    @DisplayName("시나리오 6 : 경매가 시작 -> 그 카드가 진행중으로 흘러온다")
    void startedAuctionStreamsCard() throws Exception {
        // given : 시작 시각이 막 지났고 아직 예약 상태인 경매
        long auctionId = auctionStartingNow();
        MvcResult opened = subscribe().andExpect(request().asyncStarted()).andReturn();

        // when : 이벤트를 손으로 발행하지 않는다, 그 상황에서 실제로 발행되는지까지 봐야 한다
        scheduler.advanceAuctions();

        // then : 목록에 없던 카드가 진행중 무리로 들어온다
        assertThat(body(opened))
                .contains("event:card")
                .contains("\"auctionId\":" + auctionId)
                .contains("\"phase\":\"LIVE\"");
    }

    @Test
    @DisplayName("시나리오 7 : 경매가 마감 -> 그 카드가 종료로 흘러온다")
    void closedAuctionStreamsCard() throws Exception {
        // given : 시작 전이를 먼저 소모한다. 시작과 마감이 한 틱에 겹치면 시작이 낸 카드만으로
        // 단정이 통과해 마감 리스너를 지워도 드러나지 않는다
        long auctionId = auctionStartingNow();
        scheduler.advanceAuctions();

        MvcResult opened = subscribe().andExpect(request().asyncStarted()).andReturn();

        // when : 마감(시작 + 20분)이 지나도록 시계를 민다. 입찰이 없어 유찰로 끝난다
        fixClockAt(NOW.plusMinutes(30));
        scheduler.advanceAuctions();

        // then : 마감 후 5분이 지나 복기 구간도 끝났다
        assertThat(body(opened))
                .contains("event:card")
                .contains("\"auctionId\":" + auctionId)
                .contains("\"phase\":\"CLOSED\"");
    }

    @Test
    @DisplayName("시나리오 8 : 경매방에 사람이 들어옴 -> 목록에 시청자 수가 흘러온다")
    void enteringRoomStreamsAudience() throws Exception {
        long auctionId = liveAuction();
        MvcResult list = subscribe().andExpect(request().asyncStarted()).andReturn();

        // 대역을 손으로 물리지 않는다, 경매방 구독이 목록 수로 이어지는 사슬까지 봐야 한다
        enterRoom(auctionId);

        auctionListStreamService.broadcastAudienceChanges();

        assertThat(body(list))
                .contains("event:audience")
                .contains("\"auctionId\":" + auctionId)
                .contains("\"viewerCount\":1");
    }

    @Test
    @DisplayName("시나리오 9 : 수가 그대로면 다음 주기에 아무것도 나가지 않는다")
    void unchangedAudienceStreamsNothing() throws Exception {
        long auctionId = liveAuction();
        MvcResult list = subscribe().andExpect(request().asyncStarted()).andReturn();
        enterRoom(auctionId);
        auctionListStreamService.broadcastAudienceChanges();

        int sent = audienceCount(list, auctionId);
        auctionListStreamService.broadcastAudienceChanges();

        // 조용한 방에도 매 주기 방송이 나가면 팬아웃이 보는 사람 수만큼 헛돈다
        assertThat(audienceCount(list, auctionId)).isEqualTo(sent);
    }

    private ResultActions subscribe() throws Exception {
        return mockMvc.perform(get("/api/auctions/stream"));
    }

    private void enterRoom(long auctionId) throws Exception {
        String token = sessionService.issue(users.user("한구경", Role.DEALER));

        mockMvc.perform(get("/api/auctions/{auctionId}/room/stream", auctionId)
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, token)))
                .andExpect(status().isOk());
    }

    // 경매방 명부는 싱글턴이라 앞 테스트가 남긴 방의 이벤트가 섞인다, 내 경매 것만 센다
    private static int audienceCount(MvcResult list, long auctionId) {
        return body(list).split("\"auctionId\":" + auctionId + ",\"viewerCount\"", -1).length - 1;
    }

    private ResultActions bid(long auctionId, long amount) throws Exception {
        String token = sessionService.issue(users.user("김민현", Role.DEALER));

        return mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    // 시작 15분 전에 시작해 마감이 아직 남은 방이다, 마감은 시작 + 20분이다
    private long liveAuction() {
        return room(NOW.minusMinutes(15));
    }

    private long auctionStartingNow() {
        return room(NOW.minusMinutes(1));
    }

    private long auctionEndedLongAgo() {
        return room(NOW.minusHours(1));
    }

    // 시더는 전부 예약 상태로 세운다, 진행중으로 올리는 것은 advanceAuctions 가 할 일이다
    private long room(LocalDateTime startAt) {
        return rooms.room(users.user("박판매", Role.GENERAL), startAt).startPrice(START_PRICE).create();
    }

    // 시더에 키워드 입구가 없다, 평가 결과 제출 경로를 전부 밟게 하는 값보다 한 줄이 싸다
    private void tagKeyword(long auctionId, String keyword) {
        jdbcTemplate.update("""
                insert into vehicle_keyword_tag (vehicle_id, keyword, created_at, updated_at)
                select p.vehicle_id, ?, ?, ? from auction a join auction_post p on p.id = a.post_id
                where a.id = ?
                """, keyword, NOW, NOW, auctionId);
    }

    private void deletePostOf(long auctionId) {
        jdbcTemplate.update("""
                update auction_post p join auction a on a.post_id = p.id
                set p.deleted_at = ? where a.id = ?
                """, NOW, auctionId);
    }

    // text/event-stream 에는 charset 이 안 붙어 getContentAsString() 이 ISO-8859-1 로 떨어진다
    // SSE 명세가 이 미디어타입을 항상 UTF-8 로 디코딩하게 정하므로 여기서도 그렇게 읽는다
    private static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
