package com.softeer.race.auction.presentation;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/sql/auction-create-fixture.sql")
class AuctionControllerTest {

    private static final Long VEHICLE_ID = 1000L;
    private static final DateTimeFormatter REQUEST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private AuctionPostRepository auctionPostRepository;
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
