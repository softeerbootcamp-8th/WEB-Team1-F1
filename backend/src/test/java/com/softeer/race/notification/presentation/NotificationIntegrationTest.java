package com.softeer.race.notification.presentation;

import com.jayway.jsonpath.JsonPath;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.notification.domain.Notification;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationType;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 조회와 읽음 처리를 컨트롤러에서 DB까지
 * <p>
 * <b>{@code @Transactional}을 걸지 않는다.</b> 읽음 처리가 벌크 UPDATE라 영속성 컨텍스트를 건너뛰는데,
 * 테스트에 트랜잭션을 걸면 서비스가 테스트의 컨텍스트에 참여해 {@code clearAutomatically}가 테스트가
 * 들고 있던 엔티티까지 비운다. 운영에서는 요청마다 컨텍스트가 새로 열리므로, 걸지 않는 쪽이 실제와 같다.
 * 정리는 부모의 {@code @AfterEach}가 맡는다.
 * <p>
 * <b>Clock을 고정한다.</b> auditing이 Clock 빈을 쓰므로 고정하면 이 테스트가 만드는 알림의 createdAt이
 * 전부 같은 값이 된다. 그 상태에서 커서 페이징이 흔들리지 않는다는 것이 곧 "커서를 생성 시각이 아니라
 * id로 잡았다"는 결정의 증명이다. 시각으로 끊었다면 시나리오 3이 깨진다.
 * <p>
 * <b>알림은 픽스처 SQL이 아니라 도메인 생성 경로로 만든다.</b> 문구와 링크를 NotificationType이 정하는데,
 * SQL로 직접 넣으면 그 규칙을 우회해 링크 조립이 검증되지 않는다.
 */
@DisplayName("알림 조회·읽음 처리 통합 테스트")
@Sql("/sql/notification-fixture.sql")
class NotificationIntegrationTest extends IntegrationTestSupport {

    private static final long MY_ID = 71L;
    private static final long OTHER_ID = 72L;
    private static final String MY_TOKEN = "notification-my-token";

    /** NotificationService와 같은 값이어야 커서 시나리오가 성립한다 */
    private static final int PAGE_SIZE = 10;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void fixTime() {
        fixClockAt(FIXED_NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 내 알림을 최근 것부터 한 페이지 내려준다")
    void scenario1_ListsNewestFirst() throws Exception {
        // given : 한 페이지보다 두 건 많다
        List<Long> ids = saveFor(MY_ID, PAGE_SIZE + 2);

        list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.hasNext").value(true))
                // 마지막에 만든 것이 맨 앞이다
                .andExpect(jsonPath("$.content[0].id").value(ids.getLast()))
                // 다음 커서는 이 페이지 마지막 행의 id다
                .andExpect(jsonPath("$.nextCursor").value(ids.get(ids.size() - PAGE_SIZE)))
                // 상대 시각("3분 전") 계산에 쓰라고 함께 내려준다
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    @DisplayName("시나리오 2 : 남의 알림은 목록에 없고, 읽음 처리도 없는 것으로 답한다")
    void scenario2_OtherUsersNotificationIsInvisible() throws Exception {
        saveFor(MY_ID, 2);
        List<Long> others = saveFor(OTHER_ID, 2);

        list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        // 남의 알림 : 권한 문제로 답하면 그 알림이 존재한다는 사실이 드러난다
        markRead(others.getFirst())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        // 없는 알림 : 위와 응답이 같아 요청한 쪽이 둘을 구분할 수 없다
        markRead(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        assertThat(unreadCountOf(OTHER_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("시나리오 3 : 페이지를 넘기는 사이 알림이 쌓여도 중복·누락이 없다")
    void scenario3_CursorSurvivesInsertsBetweenPages() throws Exception {
        // given : createdAt이 전부 같은 12건. 시각으로 끊는 커서라면 여기서 순서가 정해지지 않는다
        List<Long> before = saveFor(MY_ID, PAGE_SIZE + 2);

        String firstPage = list(null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Long> firstIds = idsOf(firstPage);
        long nextCursor = ((Number) JsonPath.read(firstPage, "$.nextCursor")).longValue();

        // when : 페이지를 넘기기 전에 새 알림 3건이 위에 쌓인다
        saveFor(MY_ID, 3);

        List<Long> secondIds = idsOf(list(nextCursor).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // then : 첫 페이지에서 본 것이 다시 나오지 않는다
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);

        // 두 페이지를 합치면 처음의 12건이 순서까지 그대로다. 새로 쌓인 3건은 커서 위쪽이라 보이지 않는다
        List<Long> seen = new ArrayList<>(firstIds);
        seen.addAll(secondIds);

        assertThat(seen).containsExactlyElementsOf(before.reversed());
    }

    @Test
    @DisplayName("시나리오 4 : 안 읽은 건수는 목록을 열지 않고도 받을 수 있다")
    void scenario4_UnreadCount() throws Exception {
        List<Long> mine = saveFor(MY_ID, 3);
        saveFor(OTHER_ID, 5);

        // 남의 알림 5건은 세지 않는다
        unreadCount()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));

        markRead(mine.getFirst()).andExpect(status().isNoContent());

        unreadCount().andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    @DisplayName("시나리오 5 : 같은 알림을 두 번 읽음 처리해도 성공한다")
    void scenario5_MarkReadIsIdempotent() throws Exception {
        long id = saveFor(MY_ID, 1).getFirst();

        markRead(id).andExpect(status().isNoContent());

        // 갱신 대상에서 이미 읽은 것을 빼면 여기가 404가 되고, 같은 알림을 다시 누른 사용자에게 에러가 뜬다
        markRead(id).andExpect(status().isNoContent());

        assertThat(unreadCountOf(MY_ID)).isZero();
    }

    @Test
    @DisplayName("시나리오 6 : 전체 읽음은 내 알림만 바꾸고, 읽을 것이 없어도 성공한다")
    void scenario6_MarkAllRead() throws Exception {
        saveFor(MY_ID, 4);
        saveFor(OTHER_ID, 3);

        markAllRead().andExpect(status().isNoContent());

        assertThat(unreadCountOf(MY_ID)).isZero();
        // 벌크 갱신 조건에서 소유자가 빠지면 여기가 0이 된다
        assertThat(unreadCountOf(OTHER_ID)).isEqualTo(3);

        markAllRead().andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("시나리오 7 : 참조값 대신 서버가 조립한 화면 주소가 실린다")
    void scenario7_LinkIsAssembledOnServer() throws Exception {
        User me = userRepository.findById(MY_ID).orElseThrow();
        notificationRepository.save(Notification.create(me, NotificationType.AUCTION_ENDED, 5L));
        notificationRepository.save(Notification.create(me, NotificationType.AUCTION_WON, 7L));
        notificationRepository.save(Notification.create(me, NotificationType.EVAL_APPROVED, null));

        list(null)
                .andExpect(status().isOk())
                // 참조가 필요 없는 종류는 고정 경로다
                .andExpect(jsonPath("$.content[0].link").value("/sell/result"))
                // 낙찰은 경매방이 아니라 거래로 보낸다
                .andExpect(jsonPath("$.content[1].link").value("/deals/7"))
                .andExpect(jsonPath("$.content[2].link").value("/auctions/5"))
                // 클라이언트가 종류별로 주소를 조립하지 않도록 참조값 자체는 내보내지 않는다
                .andExpect(jsonPath("$.content[0].referenceId").doesNotExist());
    }

    @Test
    @DisplayName("시나리오 8 : 세션 쿠키가 없으면 401이다")
    void scenario8_RequiresSession() throws Exception {
        // 목록은 하위 세그먼트가 없는 경로다, 인터셉터 패턴이 이것까지 잡는지 여기서 함께 드러난다
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/1/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    // ================= 준비 =================

    /**
     * 참조값을 순번으로 둬 링크가 종류와 참조로 조립된다는 것이 응답에서 보이게 한다
     *
     * @return 만든 순서대로의 식별자, 목록은 이 순서의 역순으로 나온다
     */
    private List<Long> saveFor(long userId, int count) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Long> ids = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            Notification saved = notificationRepository.save(
                    Notification.create(user, NotificationType.DEAL_STATUS_CHANGED, (long) i));
            ids.add(saved.getId());
        }

        return ids;
    }

    // ================= 요청 =================

    private ResultActions list(Long cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/notifications").cookie(sessionCookie());

        if (cursor != null) {
            request.param("cursor", String.valueOf(cursor));
        }

        return mockMvc.perform(request);
    }

    private ResultActions unreadCount() throws Exception {
        return mockMvc.perform(get("/api/notifications/unread-count").cookie(sessionCookie()));
    }

    private ResultActions markRead(long notificationId) throws Exception {
        return mockMvc.perform(
                patch("/api/notifications/{id}/read", notificationId).cookie(sessionCookie()));
    }

    private ResultActions markAllRead() throws Exception {
        return mockMvc.perform(patch("/api/notifications/read-all").cookie(sessionCookie()));
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, MY_TOKEN);
    }

    // ================= 조회 =================

    private static List<Long> idsOf(String responseBody) {
        List<Number> raw = JsonPath.read(responseBody, "$.content[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    // API가 아니라 DB를 직접 본다, 벌크 갱신이 실제로 행에 반영됐는지가 확인 대상이다
    private long unreadCountOf(long userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ? and is_read = false",
                Long.class, userId);
    }
}
