package com.softeer.race.bid.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

// 프론트는 이 표를 받아 버튼 라벨과 다음 입찰 금액을 로컬에서 계산한다
// 값과 순서가 곧 계약이므로 가짜 데이터가 아니라 확정 구간표 픽스처(@Sql)로 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/bid-increment-tiers.sql")
class BidIncrementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @DisplayName("확정한 5개 구간을 하한 오름차순으로 반환한다")
    @Test
    void returnsSeededTiers() throws Exception {
        mockMvc.perform(get("/api/bid-increments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tiers.length()").value(5))
                // 최저 구간의 하한이 0이라 모든 가격대가 어떤 구간엔 반드시 속한다
                .andExpect(jsonPath("$.tiers[0].minPrice").value(0))
                .andExpect(jsonPath("$.tiers[0].increment").value(10000))
                .andExpect(jsonPath("$.tiers[1].minPrice").value(5000000))
                .andExpect(jsonPath("$.tiers[1].increment").value(50000))
                .andExpect(jsonPath("$.tiers[2].minPrice").value(30000000))
                .andExpect(jsonPath("$.tiers[2].increment").value(100000))
                .andExpect(jsonPath("$.tiers[3].minPrice").value(60000000))
                .andExpect(jsonPath("$.tiers[3].increment").value(200000))
                .andExpect(jsonPath("$.tiers[4].minPrice").value(100000000))
                .andExpect(jsonPath("$.tiers[4].increment").value(500000));
    }

    // 엔티티를 그대로 내보내지 않기로 한 결정을 고정한다
    @DisplayName("응답에 엔티티의 id가 노출되지 않는다")
    @Test
    void doesNotExposeEntityId() throws Exception {
        mockMvc.perform(get("/api/bid-increments"))
                .andExpect(jsonPath("$.tiers[0].id").doesNotExist());
    }
}
