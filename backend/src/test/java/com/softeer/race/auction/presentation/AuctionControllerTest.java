package com.softeer.race.auction.presentation;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Sql("/sql/auction-create-fixture.sql")
class AuctionControllerTest extends IntegrationTestSupport {

    private static final Long VEHICLE_ID = 1000L;
    private static final DateTimeFormatter REQUEST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private AuctionPostRepository auctionPostRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private Clock clock;
    // @Transactional 테스트라 findById가 1차 캐시의 관리 엔티티를 그대로 돌려줄 수 있다.
    // flush + clear로 캐시를 비우고 다시 읽어야 실제 DB 반영 여부를 검증하는 셈이 된다.
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("경매글을 등록하면 경매글과 경매가 저장되고 201을 반환한다.")
    void 경매글_등록_성공() throws Exception {
        LocalDateTime startAt = LocalDateTime.now(clock).plusHours(2).withNano(0);

        mockMvc.perform(post("/api/auctions")
                        .cookie(판매자_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(startAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auctionId").exists())
                .andExpect(jsonPath("$.vehicleId").value(VEHICLE_ID))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        List<AuctionPost> posts = auctionPostRepository.findAll();
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).getThumbnailUrl()).isEqualTo("https://cdn/first.jpg");

        List<Auction> auctions = auctionRepository.findAll();
        assertThat(auctions).hasSize(1);
        Auction auction = auctions.get(0);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        assertThat(auction.getRoomOpenAt()).isEqualTo(startAt.minusMinutes(30));
        assertThat(auction.getCurrentEndTime()).isEqualTo(startAt.plusMinutes(20));
    }

    @Test
    @DisplayName("필수 값이 없으면 400을 반환한다.")
    void 필수값_누락() throws Exception {
        String invalidJson = """
                { "startPrice": 10000000 }
                """;

        mockMvc.perform(post("/api/auctions")
                        .cookie(판매자_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("시작 시각이 1시간 미만이면 400을 반환한다.")
    void 시작시각_1시간_미만() throws Exception {
        LocalDateTime tooSoon = LocalDateTime.now(clock).plusMinutes(30).withNano(0);

        mockMvc.perform(post("/api/auctions")
                        .cookie(판매자_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(tooSoon)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_AT"));
    }

    @Test
    @DisplayName("본인 소유가 아닌 차량은 경매글로 등록할 수 없다.")
    void 등록_타인소유_거부() throws Exception {
        LocalDateTime startAt = LocalDateTime.now(clock).plusHours(2).withNano(0);

        mockMvc.perform(post("/api/auctions")
                        .cookie(타인_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(startAt)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_VEHICLE_OWNER"));

        assertThat(auctionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("로그인하지 않으면 경매글을 등록할 수 없다.")
    void 등록_인증없음_거부() throws Exception {
        LocalDateTime startAt = LocalDateTime.now(clock).plusHours(2).withNano(0);

        mockMvc.perform(post("/api/auctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(startAt)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    // ================= 종료된 경매가 남은 차량의 재등록 =================
    // AuctionService 의 재등록 차단 집합을 실제로 통과시키는 테스트다.
    // 단위 테스트는 existsActiveByVehicleId 를 any() 로 스텁하므로 어떤 상태 집합을 넘겨도 통과한다.
    // 어느 상태가 재등록을 막는지는 쿼리가 실제로 도는 여기서만 확인된다.
    //
    // 유찰(FAILED) 차량의 재등록은 아직 열려 있지 않다. 차량 하나에 경매글이 하나만 존재하도록
    // 저장 구조가 막고 있어 차단 집합만 바꿔서는 열리지 않는다. 별도 이슈로 다룬다.

    @Test
    @DisplayName("낙찰된 경매가 있는 차량은 다시 경매에 올릴 수 없다.")
    void 낙찰_차량_재등록_거부() throws Exception {
        종료된_경매_남기기(낙찰자());
        LocalDateTime startAt = LocalDateTime.now(clock).plusHours(2).withNano(0);

        mockMvc.perform(post("/api/auctions")
                        .cookie(판매자_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(startAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_EXISTS"));

        assertThat(auctionRepository.findAll()).hasSize(1);
    }

    // 상태를 직접 세팅하지 않고 도메인 전이를 그대로 태운다
    // 종료는 진행 중인 경매만 대상이라 시작 전이를 먼저 거친다
    private void 종료된_경매_남기기(User winner) {
        Auction auction = 지난_경매();
        auction.start(auction.getStartTime());
        auction.close(winner, auction.getCurrentEndTime());
        auctionRepository.save(auction);
    }

    private Auction 지난_경매() {
        Vehicle vehicle = vehicleRepository.findById(VEHICLE_ID).orElseThrow();
        LocalDateTime publishedAt = LocalDateTime.now(clock).minusDays(1);
        AuctionPost post = auctionPostRepository.save(AuctionPost.create(vehicle, null, publishedAt));

        return Auction.schedule(post, 10_000_000L, publishedAt.plusHours(2));
    }

    private User 낙찰자() {
        return userRepository.save(User.create(
                "winner", "winner@race.com", "pw", "이낙찰", "01099998888", "서울 마포구", Role.DEALER));
    }

    private String requestJson(LocalDateTime startAt) {
        return """
                {
                  "vehicleId": %d,
                  "startPrice": 10000000,
                  "startAt": "%s"
                }
                """.formatted(VEHICLE_ID, startAt.format(REQUEST_FORMAT));
    }

    // ================= 경매글 수정 =================

    @Test
    @DisplayName("판매자 본인이 방 개설 전 경매를 수정하면 값이 갱신되고 200을 반환한다.")
    void 수정_성공() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Auction auction = 예약된_경매(now.plusHours(3)); // roomOpenAt = now + 2시간 30분, 편집 가능
        LocalDateTime newStartAt = now.plusHours(5).withNano(0);

        mockMvc.perform(patch("/api/auctions/{auctionId}", auction.getId())
                        .cookie(판매자_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson(20_000_000L, newStartAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startPrice").value(20_000_000))
                .andExpect(jsonPath("$.startAt").value(newStartAt.format(REQUEST_FORMAT)));

        entityManager.flush();
        entityManager.clear();

        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(updated.getStartPrice()).isEqualTo(20_000_000L);
        assertThat(updated.getStartTime()).isEqualTo(newStartAt);
        assertThat(updated.getRoomOpenAt()).isEqualTo(newStartAt.minusMinutes(30));
        assertThat(updated.getCurrentEndTime()).isEqualTo(newStartAt.plusMinutes(20));
    }

    @Test
    @DisplayName("경매방이 열린 뒤에는 수정할 수 없다.")
    void 수정_방개설후_거부() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Auction auction = 예약된_경매(now.plusMinutes(20)); // roomOpenAt = now - 10분, 이미 열림

        mockMvc.perform(patch("/api/auctions/{auctionId}", auction.getId())
                        .cookie(판매자_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson(20_000_000L, now.plusHours(2).withNano(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_ROOM_ALREADY_OPEN"));
    }

    @Test
    @DisplayName("본인 소유가 아닌 경매는 수정할 수 없다.")
    void 수정_타인소유_거부() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Auction auction = 예약된_경매(now.plusHours(3));

        mockMvc.perform(patch("/api/auctions/{auctionId}", auction.getId())
                        .cookie(타인_쿠키())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson(20_000_000L, now.plusHours(5).withNano(0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUCTION_SELLER"));
    }

    @Test
    @DisplayName("로그인하지 않으면 수정할 수 없다.")
    void 수정_인증없음_거부() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Auction auction = 예약된_경매(now.plusHours(3));

        mockMvc.perform(patch("/api/auctions/{auctionId}", auction.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson(20_000_000L, now.plusHours(5).withNano(0))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    // ================= 경매글 삭제 =================

    @Test
    @DisplayName("판매자 본인이 종료된 경매를 삭제하면 204를 반환하고 경매글에 삭제 시각이 채워진다.")
    void 삭제_성공() throws Exception {
        Auction auction = 종료된_경매();

        mockMvc.perform(delete("/api/auctions/{auctionId}", auction.getId())
                        .cookie(판매자_쿠키()))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        AuctionPost post = auctionPostRepository.findById(auction.getPost().getId()).orElseThrow();
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("종료되지 않은 경매는 삭제할 수 없다.")
    void 삭제_종료전_거부() throws Exception {
        Auction auction = 예약된_경매(LocalDateTime.now(clock).plusHours(3));

        mockMvc.perform(delete("/api/auctions/{auctionId}", auction.getId())
                        .cookie(판매자_쿠키()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_ENDED"));

        entityManager.flush();
        entityManager.clear();

        AuctionPost post = auctionPostRepository.findById(auction.getPost().getId()).orElseThrow();
        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("본인 소유가 아닌 경매는 삭제할 수 없다.")
    void 삭제_타인소유_거부() throws Exception {
        Auction auction = 종료된_경매();

        mockMvc.perform(delete("/api/auctions/{auctionId}", auction.getId())
                        .cookie(타인_쿠키()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUCTION_SELLER"));
    }

    private Auction 예약된_경매(LocalDateTime startAt) {
        Vehicle vehicle = vehicleRepository.findById(VEHICLE_ID).orElseThrow();
        LocalDateTime publishedAt = LocalDateTime.now(clock).minusDays(1);
        AuctionPost post = auctionPostRepository.save(AuctionPost.create(vehicle, null, publishedAt));

        return auctionRepository.save(Auction.schedule(post, 10_000_000L, startAt));
    }

    // 유찰(FAILED)로 종료한다, ENDED와 마찬가지로 삭제 허용 대상이다
    private Auction 종료된_경매() {
        Auction auction = 지난_경매();
        auction.start(auction.getStartTime());
        auction.close(null, auction.getCurrentEndTime());

        return auctionRepository.save(auction);
    }

    private Cookie 판매자_쿠키() {
        User seller = vehicleRepository.findById(VEHICLE_ID).orElseThrow().getSeller();
        return new Cookie(SessionCookieFactory.COOKIE_NAME, sessionService.issue(seller));
    }

    private Cookie 타인_쿠키() {
        User stranger = userRepository.save(User.create(
                "stranger", "stranger@race.com", "pw", "김타인", "01055556666", "서울 송파구", Role.GENERAL));
        return new Cookie(SessionCookieFactory.COOKIE_NAME, sessionService.issue(stranger));
    }

    private String updateRequestJson(long startPrice, LocalDateTime startAt) {
        return """
                {
                  "startPrice": %d,
                  "startAt": "%s"
                }
                """.formatted(startPrice, startAt.format(REQUEST_FORMAT));
    }
}
