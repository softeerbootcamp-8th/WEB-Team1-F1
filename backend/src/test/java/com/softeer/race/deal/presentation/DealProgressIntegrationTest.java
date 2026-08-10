package com.softeer.race.deal.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.notification.domain.NotificationType;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_SOLD;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_WON;
import static com.softeer.race.notification.domain.NotificationType.DEAL_BUYER_SCHEDULE_REQUIRED;
import static com.softeer.race.notification.domain.NotificationType.DEAL_CANCELLED;
import static com.softeer.race.notification.domain.NotificationType.DEAL_CONFIRMED;
import static com.softeer.race.notification.domain.NotificationType.DEAL_SELLER_SUBMIT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래를 컨트롤러에서 DB까지 실제로 진행시킨다
 * <p>
 * <b>거래는 낙찰 경로로 만든다.</b> 행을 직접 심으면 판매자·구매자를 테스트가 스스로 정해 버려서,
 * 엉뚱한 사람에게 차례를 주는 버그를 잡지 못한다.
 * <p>
 * <b>두 사람으로 민다.</b> 단계마다 움직일 수 있는 쪽이 정해져 있다는 것이 이 흐름의 핵심이라,
 * 한 계정으로만 밀면 그 규칙이 지켜지는지 관측되지 않는다.
 * <p>
 * <b>{@code @Transactional}을 걸지 않는다.</b> 알림 전달이 커밋을 기준으로 잡히므로, 테스트가
 * 트랜잭션을 들고 있으면 발행 시점이 실제와 달라진다. 정리는 부모의 {@code @AfterEach}가 맡는다.
 */
@DisplayName("거래 진행 통합 테스트")
class DealProgressIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);

    // 시작 11:00 → 마감 11:20, 기준 시각에는 이미 마감이 지났다
    private static final LocalDateTime STARTED_AT = NOW.minusHours(1);

    private static final String TRANSPORT_AT = "2026-08-20T14:00:00";
    private static final String DELIVERY_AT = "2026-08-21T10:00:00";

    // test/resources 의 cdn-base-url 아래, 문서로 발급했을 때 나오는 키 형태여야 통과한다
    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/123e4567-e89b-12d3-a456-426614174000.pdf";
    private static final String TRANSPORT_LOCATION = "서울시 강남구 테헤란로 123";
    private static final String DELIVERY_LOCATION = "부산시 해운대구 센텀중앙로 55";

    private static final long START_PRICE = 30_000_000L;

    private static final String BUYER_TOKEN = "deal-progress-buyer-token";
    private static final String SELLER_TOKEN = "deal-progress-seller-token";
    private static final String STRANGER_TOKEN = "deal-progress-stranger-token";

    @Autowired
    private NotificationRepository notificationRepository;

    private User buyer;
    private User seller;
    private User stranger;

    private long dealId;

    @BeforeEach
    void seed() {
        fixClockAt(NOW);

        buyer = users.user("김구매", Role.DEALER);
        seller = users.user("박판매", Role.GENERAL);
        stranger = users.user("최타인", Role.GENERAL);

        login(buyer, BUYER_TOKEN);
        login(seller, SELLER_TOKEN);
        login(stranger, STRANGER_TOKEN);

        dealId = boughtDeal();
    }

    @Test
    @DisplayName("시나리오 1 : 낙찰부터 확정까지 세 번의 요청으로 끝나고 값이 상세에 남는다")
    void scenario1_RunsToConfirmed() throws Exception {
        // 낙찰 직후에는 구매자 차례다
        detail(BUYER_TOKEN)
                .andExpect(jsonPath("$.status").value("BUYER_CONFIRM_PENDING"))
                .andExpect(jsonPath("$.actionRequired").value(true));

        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());
        submitTransport(SELLER_TOKEN, TRANSPORT_AT).andExpect(status().isNoContent());
        confirmDelivery(BUYER_TOKEN, DELIVERY_AT).andExpect(status().isNoContent());

        // 양쪽이 같은 정보를 본다, 약속 하나에 두 사람이 나가야 한다
        detail(SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.documentUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.transportAt").value(TRANSPORT_AT))
                .andExpect(jsonPath("$.transportLocation").value(TRANSPORT_LOCATION))
                .andExpect(jsonPath("$.deliveryAt").value(DELIVERY_AT))
                .andExpect(jsonPath("$.deliveryLocation").value(DELIVERY_LOCATION))

                // 끝난 거래는 기다리는 쪽이 없다, 양쪽 다 버튼이 꺼진다
                .andExpect(jsonPath("$.actionRequired").value(false));

        detail(BUYER_TOKEN).andExpect(jsonPath("$.actionRequired").value(false));
    }

    @Test
    @DisplayName("시나리오 2 : 상대 차례에 보낸 요청은 403 이고 단계가 움직이지 않는다")
    void scenario2_RejectsWrongTurn() throws Exception {
        // 구매 확정 전에 판매자가 먼저 서류를 내려 한다
        submitTransport(SELLER_TOKEN, TRANSPORT_AT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));

        // 구매 확정은 구매자만 누른다
        confirmPurchase(SELLER_TOKEN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));

        detail(BUYER_TOKEN).andExpect(jsonPath("$.status").value("BUYER_CONFIRM_PENDING"));
    }

    @Test
    @DisplayName("시나리오 3 : 당사자가 아닌 사람에게는 404 다, 403 과 갈리지 않는다")
    void scenario3_HidesDealFromStranger() throws Exception {
        // 403 으로 갈리면 그 번호의 거래가 존재한다는 사실이 새어 나간다
        confirmPurchase(STRANGER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        cancel(STRANGER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 4 : 같은 요청이 두 번 도착해도 단계는 한 번만 움직인다")
    void scenario4_RetransmissionMovesNothing() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());

        // 두 번째는 차례가 판매자로 넘어가 있어 403 이 먼저 걸린다
        confirmPurchase(BUYER_TOKEN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));

        detail(BUYER_TOKEN).andExpect(jsonPath("$.status").value("SELLER_SUBMIT_PENDING"));
    }

    @Test
    @DisplayName("시나리오 5 : 탁송 일시가 과거면 400 이고 단계가 움직이지 않는다")
    void scenario5_RejectsPastTransport() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());

        submitTransport(SELLER_TOKEN, "2026-08-09T11:59:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAST_TRANSPORT_SCHEDULE"));

        // 값 검증에서 걸리면 앞서 바뀐 단계도 함께 롤백돼야 한다
        detail(SELLER_TOKEN).andExpect(jsonPath("$.status").value("SELLER_SUBMIT_PENDING"));
    }

    @Test
    @DisplayName("시나리오 5-1 : 우리가 발급하지 않은 서류 주소는 400 이다")
    void scenario5_1_RejectsUnmanagedDocument() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());

        // 남의 도메인. 저장되면 거래 화면이 통제할 수 없는 곳을 서류라고 가리킨다
        submitTransport(SELLER_TOKEN, TRANSPORT_AT, "https://evil.example.com/x.pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNMANAGED_DOCUMENT_URL"));

        // 우리가 발급했지만 이미지다. "우리 주소인가"만 물으면 여기서 뚫린다
        submitTransport(SELLER_TOKEN, TRANSPORT_AT,
                CDN_BASE_URL + "/images/2026/08/123e4567-e89b-12d3-a456-426614174000.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNMANAGED_DOCUMENT_URL"));

        detail(SELLER_TOKEN).andExpect(jsonPath("$.status").value("SELLER_SUBMIT_PENDING"));
    }

    @Test
    @DisplayName("시나리오 6 : 인도 일시가 탁송보다 앞서면 400 이다")
    void scenario6_RejectsDeliveryBeforeTransport() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());
        submitTransport(SELLER_TOKEN, TRANSPORT_AT).andExpect(status().isNoContent());

        // 현재보다는 미래여도 탁송보다 앞설 수 있다, 그러면 출발 전에 받는 약속이 된다
        confirmDelivery(BUYER_TOKEN, "2026-08-19T10:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_BEFORE_TRANSPORT"));

        detail(BUYER_TOKEN).andExpect(jsonPath("$.status").value("BUYER_SCHEDULE_PENDING"));
    }

    @Test
    @DisplayName("시나리오 7 : 단계마다 다음에 움직일 사람에게만 알림이 가고 확정만 양쪽에 간다")
    void scenario7_NotifiesNextActorOnly() throws Exception {
        // 낙찰 알림이 이미 한 건씩 쌓여 있다, 여기서부터가 거래 알림이다
        assertThat(typesOf(buyer)).containsExactly(AUCTION_WON);
        assertThat(typesOf(seller)).containsExactly(AUCTION_SOLD);

        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());

        // 구매자는 자기가 한 일을 다시 통보받지 않는다, 알림은 "네가 할 일"이라는 신호다
        assertThat(typesOf(seller)).containsExactly(AUCTION_SOLD, DEAL_SELLER_SUBMIT_REQUIRED);
        assertThat(typesOf(buyer)).containsExactly(AUCTION_WON);

        submitTransport(SELLER_TOKEN, TRANSPORT_AT).andExpect(status().isNoContent());

        assertThat(typesOf(buyer)).containsExactly(AUCTION_WON, DEAL_BUYER_SCHEDULE_REQUIRED);

        confirmDelivery(BUYER_TOKEN, DELIVERY_AT).andExpect(status().isNoContent());

        // 확정만 양쪽이다, 그 날짜에는 둘 다 나가야 한다
        assertThat(typesOf(buyer))
                .containsExactly(AUCTION_WON, DEAL_BUYER_SCHEDULE_REQUIRED, DEAL_CONFIRMED);
        assertThat(typesOf(seller))
                .containsExactly(AUCTION_SOLD, DEAL_SELLER_SUBMIT_REQUIRED, DEAL_CONFIRMED);
    }

    @Test
    @DisplayName("시나리오 8 : 알림 딥링크는 전부 그 거래를 가리킨다")
    void scenario8_LinksToTheDeal() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());
        submitTransport(SELLER_TOKEN, TRANSPORT_AT).andExpect(status().isNoContent());
        confirmDelivery(BUYER_TOKEN, DELIVERY_AT).andExpect(status().isNoContent());

        // 눌렀을 때 할 일이 있는 화면으로 가야 알림이 제 역할을 한다
        assertThat(notificationsOf(seller))
                .filteredOn(row -> row.type() != AUCTION_SOLD)
                .allSatisfy(row -> assertThat(row.link()).isEqualTo("/deals/" + dealId));
    }

    @Test
    @DisplayName("시나리오 9 : 그만둔 쪽이 귀책으로 남고 상대에게 알림이 간다")
    void scenario9_CancelLeavesFault() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());

        // 자기 차례가 아닌 구매자도 그만둘 수 있다, 상대를 기다리는 쪽도 빠질 수 있어야 한다
        cancel(BUYER_TOKEN).andExpect(status().isNoContent());

        detail(SELLER_TOKEN)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("BUYER_CANCELLED"))
                .andExpect(jsonPath("$.faultParty").value("BUYER"));

        assertThat(typesOf(seller)).endsWith(DEAL_CANCELLED);
    }

    @Test
    @DisplayName("시나리오 10 : 확정된 거래는 취소할 수 없다")
    void scenario10_CannotCancelConfirmed() throws Exception {
        confirmPurchase(BUYER_TOKEN).andExpect(status().isNoContent());
        submitTransport(SELLER_TOKEN, TRANSPORT_AT).andExpect(status().isNoContent());
        confirmDelivery(BUYER_TOKEN, DELIVERY_AT).andExpect(status().isNoContent());

        // 그때부터는 서비스가 아니라 서로 연락해서 정할 일이다
        cancel(SELLER_TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CANCELLABLE"));

        detail(SELLER_TOKEN).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private ResultActions confirmPurchase(String token) throws Exception {
        return mockMvc.perform(post("/api/deals/{dealId}/confirmation", dealId)
                .cookie(sessionCookie(token)));
    }

    private ResultActions submitTransport(String token, String transportAt) throws Exception {
        return submitTransport(token, transportAt, DOCUMENT_URL);
    }

    private ResultActions submitTransport(String token, String transportAt, String documentUrl)
            throws Exception {
        return mockMvc.perform(post("/api/deals/{dealId}/transport", dealId)
                .cookie(sessionCookie(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"documentUrl": "%s", "transportAt": "%s", "transportLocation": "%s"}
                        """.formatted(documentUrl, transportAt, TRANSPORT_LOCATION)));
    }

    private ResultActions confirmDelivery(String token, String deliveryAt) throws Exception {
        return mockMvc.perform(post("/api/deals/{dealId}/delivery", dealId)
                .cookie(sessionCookie(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"deliveryAt": "%s", "deliveryLocation": "%s"}
                        """.formatted(deliveryAt, DELIVERY_LOCATION)));
    }

    private ResultActions cancel(String token) throws Exception {
        return mockMvc.perform(post("/api/deals/{dealId}/cancellation", dealId)
                .cookie(sessionCookie(token)));
    }

    private ResultActions detail(String token) throws Exception {
        return mockMvc.perform(get("/api/deals/{dealId}", dealId).cookie(sessionCookie(token)))
                .andExpect(status().isOk());
    }

    /** 내가 산 거래, 판매자는 상대다. 낙찰까지 프로덕션 경로로 밟는다 */
    private long boughtDeal() {
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), buyer, START_PRICE)
                .closed()
                .create();

        return jdbcTemplate.queryForObject(
                "select id from deal where auction_id = ?", Long.class, auctionId);
    }

    // 오래된 것부터 본다, 단계가 밟힌 순서 그대로 쌓였는지가 검증 대상이다
    private List<NotificationRow> notificationsOf(User user) {
        return notificationRepository.findPage(user.getId(), Long.MAX_VALUE, Limit.of(10))
                .reversed();
    }

    private List<NotificationType> typesOf(User user) {
        return notificationsOf(user).stream().map(NotificationRow::type).toList();
    }

    // 로그인 경로 대신 세션을 직접 심는다, 이 테스트가 볼 것은 인증이 아니라 거래 진행이다
    private void login(User user, String rawToken) {
        jdbcTemplate.update("""
                        insert into user_session (id, user_id, expires_at, created_at, updated_at)
                        values (sha2(?, 256), ?, ?, ?, ?)
                        """,
                rawToken, user.getId(), NOW.plusHours(1), NOW, NOW);
    }

    private Cookie sessionCookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }
}
