package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListChannel;
import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 목록 변화 구독을 컨트롤러에서 채널까지
 * <p>
 * 단위테스트로는 볼 수 없는 것만 여기서 고정한다. 응답이 끝나지 않고 열린 채 남는지,
 * 미디어타입이 맞는지, 세션 없이도 열리는지다. 셋 다 객체를 돌려받아서는 알 수 없다.
 */
@DisplayName("경매 목록 스트림 통합 테스트")
class AuctionListStreamIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AuctionListChannel auctionListChannel;

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

    private ResultActions subscribe() throws Exception {
        return mockMvc.perform(get("/api/auctions/stream"));
    }

    // text/event-stream 에는 charset 이 안 붙어 getContentAsString() 이 ISO-8859-1 로 떨어진다
    // SSE 명세가 이 미디어타입을 항상 UTF-8 로 디코딩하게 정하므로 여기서도 그렇게 읽는다
    private static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
