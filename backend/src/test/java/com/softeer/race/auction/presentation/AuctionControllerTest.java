package com.softeer.race.auction.presentation;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleRepository;
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
    private Clock clock;

    @Test
    @DisplayName("경매글을 등록하면 경매글과 경매가 저장되고 201을 반환한다.")
    void 경매글_등록_성공() throws Exception {
        LocalDateTime startAt = LocalDateTime.now(clock).plusHours(2).withNano(0);

        mockMvc.perform(post("/api/auctions")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(tooSoon)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_AT"));
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
}
