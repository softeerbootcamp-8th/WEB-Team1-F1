package com.softeer.race.deal.presentation;

import com.jayway.jsonpath.JsonPath;
import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.deal.domain.CancellationReason;
import com.softeer.race.deal.domain.Deal;
import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래 목록·상세를 컨트롤러에서 DB까지
 * <p>
 * <b>거래는 낙찰 경로로 만든다.</b> 행을 직접 심으면 판매자·구매자·낙찰가를 테스트가 스스로 정해
 * 버려서, 조회가 엉뚱한 사람을 상대로 보여 주는 버그를 잡지 못한다.
 * <p>
 * <b>모든 경매를 같은 시각에 마감시킨다.</b> 그러면 거래의 단계 변경 시각이 전부 같아지고, 그 상태에서
 * 커서 페이징이 흔들리지 않는다는 것이 곧 "커서를 시각이 아니라 식별자로 잡았다"는 결정의 증명이다.
 * <p>
 * <b>{@code @Transactional}을 걸지 않는다.</b> 요청마다 컨텍스트가 새로 열리는 실제 경로와 같게 두려는
 * 것이다. 정리는 부모의 {@code @AfterEach}가 맡는다.
 */
@DisplayName("거래 조회 통합 테스트")
class DealQueryIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);

    // 시작 11:00 → 마감 11:20, 기준 시각에는 이미 마감이 지났다
    private static final LocalDateTime STARTED_AT = NOW.minusHours(1);

    // 마감 시각에 낙찰이 확정되고 거래가 열린다
    // LocalDateTime.toString() 은 초가 0이면 떼지만 JSON 직렬화는 붙이므로 문자열로 고정한다
    private static final String OPENED_AT = "2026-08-09T11:20:00";

    private static final long START_PRICE = 30_000_000L;

    /** DealQueryService 와 같은 값이어야 커서 시나리오가 성립한다 */
    private static final int PAGE_SIZE = 10;

    private static final String MY_TOKEN = "deal-query-my-token";

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionPostRepository auctionPostRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User me;
    private User partner;
    private User stranger;

    @BeforeEach
    void seedUsers() {
        fixClockAt(NOW);

        me = users.user("김구매", Role.DEALER);
        partner = users.user("박판매", Role.GENERAL);
        stranger = users.user("최타인", Role.GENERAL);

        login(me);
    }

    @Test
    @DisplayName("시나리오 1 : 판 거래와 산 거래가 한 목록에 최신순으로 섞이고 내 쪽이 각각 맞다")
    void scenario1_ListsBothSidesMixed() throws Exception {
        // given : 팔고, 사고, 다시 판 순서
        long soldFirst = soldDeal();
        long bought = boughtDeal();
        long soldLast = soldDeal();

        list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))

                // 최근에 만들어진 것이 맨 앞이다
                .andExpect(jsonPath("$.content[0].dealId").value(soldLast))
                .andExpect(jsonPath("$.content[1].dealId").value(bought))
                .andExpect(jsonPath("$.content[2].dealId").value(soldFirst))

                // 판매와 구매를 따로 뽑아 합쳤다면 이 순서가 나오지 않는다
                .andExpect(jsonPath("$.content[0].mySide").value("SELLER"))
                .andExpect(jsonPath("$.content[1].mySide").value("BUYER"))
                .andExpect(jsonPath("$.content[2].mySide").value("SELLER"))

                // 상대 시각 표시에 쓰라고 함께 내려준다
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    @DisplayName("시나리오 2 : 내가 당사자가 아닌 거래는 목록에 없다")
    void scenario2_HidesOtherPeoplesDeal() throws Exception {
        long mine = soldDeal();
        strangerDeal();

        list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(mine));
    }

    @Test
    @DisplayName("시나리오 3 : 상대 이름은 가운데를 가려서 내려준다")
    void scenario3_MasksCounterpartName() throws Exception {
        soldDeal();

        list(null)
                .andExpect(status().isOk())
                // 상대는 박판매다, 실명이 그대로 나가면 되돌릴 수 없다
                .andExpect(jsonPath("$.content[0].counterpartName").value("박*매"))
                .andExpect(jsonPath("$.content[0].finalPrice").value(START_PRICE));
    }

    @Test
    @DisplayName("시나리오 4 : 페이지를 넘기면 중복도 누락도 없이 이어 읽는다")
    void scenario4_ReadsNextPageWithoutGapOrDuplicate() throws Exception {
        // given : 한 페이지보다 두 건 많다, 전부 같은 시각에 마감돼 단계 변경 시각이 같다
        List<Long> dealIds = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE + 2; i++) {
            dealIds.add(soldDeal());
        }

        String nextCursor = list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].dealId").value(dealIds.getLast()))
                .andReturn().getResponse().getContentAsString();

        Long cursor = cursorOf(nextCursor);

        // when : 받은 커서를 그대로 돌려보낸다
        list(cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())

                // 남은 두 건은 가장 먼저 만들어진 것들이다
                .andExpect(jsonPath("$.content[0].dealId").value(dealIds.get(1)))
                .andExpect(jsonPath("$.content[1].dealId").value(dealIds.getFirst()));
    }

    @Test
    @DisplayName("시나리오 5 : 읽는 도중 새 거래가 생겨도 이미 읽은 페이지가 밀리지 않는다")
    void scenario5_NewDealDoesNotShiftReadPages() throws Exception {
        List<Long> dealIds = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE + 1; i++) {
            dealIds.add(soldDeal());
        }

        Long cursor = cursorOf(
                list(null).andExpect(jsonPath("$.content.length()").value(PAGE_SIZE))
                        .andReturn().getResponse().getContentAsString());

        // when : 첫 페이지를 읽은 뒤 새 거래가 생긴다
        long added = soldDeal();

        // then : 두 번째 페이지에는 가장 오래된 한 건만 있다
        // 커서를 시각으로 잡았다면 단계 변경 시각이 모두 같아 여기가 무너진다
        list(cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(dealIds.getFirst()));

        // 새 거래는 다시 첫 페이지를 읽을 때 맨 앞에 나타난다
        list(null).andExpect(jsonPath("$.content[0].dealId").value(added));
    }

    @Test
    @DisplayName("시나리오 6 : 내 거래 상세에 차량·금액·단계·상대가 담긴다")
    void scenario6_ShowsDetail() throws Exception {
        long dealId = boughtDeal();

        detail(dealId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealId").value(dealId))
                .andExpect(jsonPath("$.mySide").value("BUYER"))
                .andExpect(jsonPath("$.status").value("BUYER_CONFIRM_PENDING"))
                .andExpect(jsonPath("$.finalPrice").value(START_PRICE))
                .andExpect(jsonPath("$.counterpartName").value("박*매"))
                .andExpect(jsonPath("$.model").exists())
                .andExpect(jsonPath("$.modelYear").exists())
                .andExpect(jsonPath("$.mileage").exists())

                // 거래가 열린 시각은 낙찰이 확정된 시각이다, 요청 시각이 아니다
                .andExpect(jsonPath("$.openedAt").value(OPENED_AT))

                // 취소되지 않았으므로 사유와 귀책이 없다
                .andExpect(jsonPath("$.cancellationReason").doesNotExist())
                .andExpect(jsonPath("$.faultParty").doesNotExist());
    }

    @Test
    @DisplayName("시나리오 7 : 남의 거래와 없는 거래가 구별되지 않는다")
    void scenario7_OthersDealIsIndistinguishableFromMissing() throws Exception {
        long othersDeal = strangerDeal();

        String others = detail(othersDeal)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String missing = detail(othersDeal + 10_000)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 응답이 갈리면 그 번호의 거래가 존재한다는 사실이 새어 나간다
        assertThat(codeOf(others)).isEqualTo(codeOf(missing));
        assertThat(detailMessageOf(others)).isEqualTo(detailMessageOf(missing));
    }

    @Test
    @DisplayName("시나리오 8 : 취소된 거래는 사유와 귀책이 함께 온다")
    void scenario8_ShowsCancellation() throws Exception {
        long dealId = boughtDeal();
        cancel(dealId, CancellationReason.BUYER_CANCELLED);

        detail(dealId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("BUYER_CANCELLED"))

                // 귀책은 사유에서 계산되지만 서버가 풀어 준다, 보증금 향방은 서버 정책이다
                .andExpect(jsonPath("$.faultParty").value("BUYER"));
    }

    @Test
    @DisplayName("시나리오 9 : 경매글이 삭제돼도 거래는 목록과 상세에 남는다")
    void scenario9_KeepsDealAfterPostDeleted() throws Exception {
        long dealId = boughtDeal();
        deletePostOf(dealId);

        // 경매 목록 쿼리를 그대로 옮겨 오면 삭제 조건이 딸려 와, 판매자가 글을 내린 순간
        // 구매자의 거래가 사라진다
        list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].dealId").value(dealId));

        detail(dealId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("시나리오 10 : 거래가 늘어도 목록 조회에 나가는 쿼리 수가 그대로다")
    void scenario10_QueryCountDoesNotGrowWithDeals() throws Exception {
        // given : 두 건일 때의 쿼리 수
        soldDeal();
        soldDeal();
        long withTwo = queryCountWhileListing();

        // when : 한 페이지를 꽉 채울 만큼 늘린다
        for (int i = 2; i < PAGE_SIZE; i++) {
            soldDeal();
        }

        long withFullPage = queryCountWhileListing();

        // then : 연관을 하나씩 따라가는 구조라면 거래 수만큼 조회가 늘어난다
        list(null).andExpect(jsonPath("$.content.length()").value(PAGE_SIZE));
        assertThat(withFullPage).isEqualTo(withTwo);

        // 통계가 꺼져 있으면 양쪽이 0이라 위 비교가 그냥 통과한다, 실제로 세고 있는지 확인한다
        // 상한은 건수와 무관하다는 뜻이다, 페이지를 꽉 채워도 거래 수보다 적게 나간다
        assertThat(withFullPage).isPositive().isLessThan(PAGE_SIZE);
    }

    // 목록 요청 한 번에 실제로 나간 JDBC 문 수, 인증 조회처럼 건수와 무관한 것도 함께 세지만
    // 양쪽에 똑같이 얹히므로 두 값을 비교하는 데는 영향이 없다
    private long queryCountWhileListing() throws Exception {
        Statistics statistics = statistics();
        statistics.clear();

        list(null).andExpect(status().isOk());

        return statistics.getPrepareStatementCount();
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        return statistics;
    }

    /** 내가 판 거래, 낙찰자는 상대다 */
    private long soldDeal() {
        return dealIdOf(closedAuction(me, partner));
    }

    /** 내가 산 거래, 판매자는 상대다 */
    private long boughtDeal() {
        return dealIdOf(closedAuction(partner, me));
    }

    /** 내가 끼지 않은 거래 */
    private long strangerDeal() {
        return dealIdOf(closedAuction(partner, stranger));
    }

    // 낙찰까지 프로덕션 경로로 밟는다, 거래도 그 경로에서 만들어진다
    private long closedAuction(User seller, User winner) {
        return rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), winner, START_PRICE)
                .closed()
                .create();
    }

    private long dealIdOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select id from deal where auction_id = ?", Long.class, auctionId);
    }

    private void cancel(long dealId, CancellationReason reason) {
        Deal deal = dealRepository.findById(dealId).orElseThrow();
        deal.cancel(reason, NOW);
        dealRepository.save(deal);
    }

    private void deletePostOf(long dealId) {
        long auctionId = jdbcTemplate.queryForObject(
                "select auction_id from deal where id = ?", Long.class, dealId);

        Auction auction = auctionRepository.findWithPostById(auctionId).orElseThrow();
        AuctionPost post = auction.getPost();
        post.delete(NOW);
        auctionPostRepository.save(post);
    }

    // 로그인 경로 대신 세션을 직접 심는다, 이 테스트가 볼 것은 인증이 아니라 조회다
    // 만료를 넉넉히 둬 슬라이딩 연장에 걸리지 않게 한다, 걸리면 조회마다 UPDATE 가 섞인다
    private void login(User user) {
        jdbcTemplate.update("""
                        insert into user_session (id, user_id, expires_at, created_at, updated_at)
                        values (sha2(?, 256), ?, ?, ?, ?)
                        """,
                MY_TOKEN, user.getId(), NOW.plusHours(1), NOW, NOW);
    }

    private ResultActions list(Long cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/deals").cookie(sessionCookie());

        if (cursor != null) {
            request = request.param("cursor", String.valueOf(cursor));
        }

        return mockMvc.perform(request);
    }

    private ResultActions detail(long dealId) throws Exception {
        return mockMvc.perform(get("/api/deals/{dealId}", dealId).cookie(sessionCookie()));
    }

    private Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, MY_TOKEN);
    }

    private Long cursorOf(String body) {
        return JsonPath.parse(body).read("$.nextCursor", Long.class);
    }

    private String codeOf(String body) {
        return JsonPath.parse(body).read("$.code", String.class);
    }

    private String detailMessageOf(String body) {
        return JsonPath.parse(body).read("$.detail", String.class);
    }
}
