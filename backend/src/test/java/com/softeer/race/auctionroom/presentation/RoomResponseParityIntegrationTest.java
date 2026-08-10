package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같은 방을 조회와 방송으로 받으면 같은 모양인지, 컨트롤러에서 DB까지
 * <p>
 * 조회와 방송이 같은 값을 다른 모양으로 주면 화면이 같은 정보를 두 방법으로 읽어야 한다. 그 규칙은
 * 지금 {@code AuctionRoomView.of} 주석에만 있고 아무도 실행하지 않는다. 여기서 실행한다.
 * <p>
 * 필드 이름을 하나씩 적지 않는 것이 이 테스트의 핵심이다. 양쪽에 필드가 늘어도 계속 유효하고
 * 한쪽에만 늘면 깨진다. 다를 수 있는 것은 보는 사람 기준의 판정 둘뿐이고, 방송은 보는 사람이
 * 정해지지 않으므로 그 둘은 값이 아니라 키 자체가 없어야 한다.
 * <p>
 * 열다섯 인자를 위치로 채우는 방송 직렬화의 그물도 여기 걸린다. 조회는 그대로인데 방송만
 * 어긋나면 대조가 깨진다.
 */
@DisplayName("경매방 조회·방송 응답 일치 통합 테스트")
class RoomResponseParityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SessionService sessionService;

    // Boot 4 의 Jackson auto-config 가 등록하는 것은 JsonMapper 다, 응용이 쓰는 것과 같은 것으로 읽는다
    @Autowired
    private JsonMapper jsonMapper;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 마감(시작 + 20분)은 지났고 결과 확인 5분은 안 지난 시각이 되도록 잡는다
    // RESULT 단계라야 조회와 구독이 둘 다 열려 한 시나리오로 두 경로를 다 탄다
    private static final LocalDateTime RESULT_START_AT = LocalDateTime.of(2026, 8, 3, 20, 23);

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("낙찰까지 끝난 방을 조회와 방송으로 받으면 -> 개인화 둘을 빼고 완전히 같다")
    void queryAndBroadcast_MatchExceptPersonalization() throws Exception {
        // given : 두 사람이 네 번 넣어 이준호가 낙찰됐고, 보는 사람이 그 낙찰자다
        User winner = users.user("이준호", Role.DEALER);
        User loser = users.user("남궁민수", Role.DEALER);

        long auctionId = resultRoomWonBy(winner, loser);
        String session = sessionService.issue(winner);

        // when : 같은 사람이 구독으로 첫 현황을 받고 같은 고정 시각에 조회한다
        // 구독이 먼저다, 그래야 접속자 수가 양쪽 모두 1로 같다
        MvcResult subscribed = subscribe(auctionId, session);
        JsonNode broadcast = firstBroadcast(subscribed);
        JsonNode query = queryRoom(auctionId, session);

        // then 1 : 개인화 둘이 조회에서 실제로 참이다, 이 단정이 없으면 아래 대조가 공허해진다
        assertThat(query.at("/winner/mine").asBoolean()).isTrue();
        assertThat(query.at("/recentBids/0/mine").asBoolean()).isTrue();

        // then 2 : 방송은 한 벌을 모두에게 보내므로 그 둘은 거짓이 아니라 키가 없어야 한다
        assertThat(broadcast.at("/winner/mine").isMissingNode()).isTrue();
        assertThat(broadcast.at("/recentBids/0/mine").isMissingNode()).isTrue();

        // then 2-1 : 차량은 방 안에서 바뀌지 않으므로 방송에 실리지 않는다, 조회가 한 번 준다
        assertThat(broadcast.at("/vehicle").isMissingNode()).isTrue();
        assertThat(query.at("/vehicle").isMissingNode()).isFalse();

        // then 3 : 그 둘을 걷어내면 나머지는 키도 값도 완전히 같다
        assertSameTree("", broadcast, withoutPersonalization(query));
    }

    // ================= 대조 ====================
    // 트리를 통째로 비교하지 않는다, 깨질 때 두 트리가 다르다는 것만 나와 어느 필드인지 알 수 없다
    // 키 합집합을 돌며 경로를 달고 단정해 실패 메시지에 필드 이름이 찍히게 한다
    private static void assertSameTree(String path, JsonNode broadcast, JsonNode query) {
        if (query.isObject() || broadcast.isObject()) {
            for (String key : fieldUnion(broadcast, query)) {
                String child = path.isEmpty() ? key : path + "." + key;

                assertThat(broadcast.has(key))
                        .as("%s 키가 한쪽에만 있다", child)
                        .isEqualTo(query.has(key));

                assertSameTree(child, broadcast.path(key), query.path(key));
            }
            return;
        }

        if (query.isArray() || broadcast.isArray()) {
            assertThat(broadcast.size()).as("%s 길이", path).isEqualTo(query.size());

            for (int index = 0; index < query.size(); index++) {
                assertSameTree(path + "[" + index + "]", broadcast.get(index), query.get(index));
            }
            return;
        }

        assertThat(broadcast).as("%s 값", path).isEqualTo(query);
    }

    // 정렬해서 돌린다, 깨지는 필드가 실행마다 달라지면 실패 메시지를 비교할 수 없다
    private static Set<String> fieldUnion(JsonNode broadcast, JsonNode query) {
        Set<String> keys = new TreeSet<>(broadcast.propertyNames());
        keys.addAll(query.propertyNames());

        return keys;
    }

    // 방송에 없는 것은 보는 사람 기준의 판정 둘과 방 안에서 바뀌지 않는 차량이다
    private JsonNode withoutPersonalization(JsonNode query) {
        ObjectNode copy = (ObjectNode) query.deepCopy();

        ((ObjectNode) copy.get("winner")).remove("mine");
        copy.get("recentBids").forEach(bid -> ((ObjectNode) bid).remove("mine"));
        copy.remove("vehicle");

        return copy;
    }

    // ================= 준비 ====================
    // 마감 30초 안쪽에 넣으면 연장이 걸려 단계가 달라진다, 마지막 입찰을 그 밖에 둔다
    private long resultRoomWonBy(User winner, User loser) {
        return rooms.room(users.user("최판매", Role.GENERAL), RESULT_START_AT)
                .photos("https://cdn.race.dev/seltos-1.jpg", "https://cdn.race.dev/seltos-2.jpg")
                .startPrice(20_000_000L)
                .bid(RESULT_START_AT.plusMinutes(5), loser, 21_000_000L)
                .bid(RESULT_START_AT.plusMinutes(10), winner, 22_000_000L)
                .bid(RESULT_START_AT.plusMinutes(15), loser, 23_000_000L)
                .bid(RESULT_START_AT.plusMinutes(18), winner, 24_000_000L)
                .closed()
                .create();
    }

    // ================= 요청 ====================
    private MvcResult subscribe(long auctionId, String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/stream", auctionId)
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)))
                .andExpectAll(status().isOk(), request().asyncStarted())
                .andReturn();
    }

    private JsonNode queryRoom(long auctionId, String sessionToken) throws Exception {
        String body = mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return jsonMapper.readTree(body);
    }

    // SSE 는 현황 하나가 data 한 줄이다, 첫 줄만 떼어 읽는다
    // text/event-stream 에는 charset 이 안 붙어 getContentAsString() 이 ISO-8859-1 로 떨어진다
    private JsonNode firstBroadcast(MvcResult subscribed) throws Exception {
        String body = new String(subscribed.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        String json = body.lines()
                .filter(line -> line.startsWith("data:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("구독 직후 첫 현황이 오지 않았다"))
                .substring("data:".length());

        return jsonMapper.readTree(json);
    }
}