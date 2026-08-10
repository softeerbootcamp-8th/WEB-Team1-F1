package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.application.dto.AuctionListCursor;
import com.softeer.race.auctionlist.application.dto.AuctionListInfo;
import com.softeer.race.auctionlist.domain.AuctionListGroup;
import com.softeer.race.auctionlist.domain.AuctionListRepository;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.auctionroom.application.RoomChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 그룹을 넘나들며 한 페이지를 채우는 순회 로직
 * <p>
 * 레포지토리 테스트는 쿼리 하나하나가 맞는지 본다. 여기서는 그것들을 엮는 부분만 본다.
 * 레포지토리를 목으로 두면 각 그룹에 어떤 커서 값이 넘어갔는지 직접 확인할 수 있다.
 * <p>
 * 노리는 실수 두 가지
 * <p>
 * 1. 커서가 있던 그룹을 이어 읽지 않고 처음부터 읽는 경우
 * 다음 페이지가 이전 페이지를 그대로 돌려주어 무한 스크롤이 첫 페이지에 갇힌다.
 * <p>
 * 2. 예정 그룹 커서에 시작 시각 대신 마감 시각을 담는 경우
 * 마감이 시작 + 20분이라 커서가 20분 앞서고, 그 사이에 시작하는 예정 경매가 통째로 사라진다.
 */
@DisplayName("경매글 목록 페이지 조립 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionListServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.atZone(KST).toInstant(), KST);

    private static final int PAGE_SIZE = 20;
    private static final int FETCH_SIZE = PAGE_SIZE + 1;   // 다음 페이지 유무 판단용으로 한 건 더

    private static final LocalDateTime ASC_START = LocalDateTime.of(1000, 1, 1, 0, 0);
    private static final LocalDateTime DESC_START = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private static final long SELLER_ID = 7L;

    @Mock
    private AuctionListRepository auctionListRepository;

    @Mock
    private RoomChannel roomChannel;

    private AuctionListService auctionListService;

    @BeforeEach
    void setUp() {
        auctionListService = new AuctionListService(auctionListRepository, roomChannel, FIXED_CLOCK);
    }

    // ================= 그룹 순회 =================

    @Test
    @DisplayName("한 그룹으로 페이지가 다 차면 다음 그룹은 조회하지 않는다")
    void stopsOncePageIsFull() {
        // given : 진행중만으로 21건이 나온다
        givenLive(liveRows(1, FETCH_SIZE));

        // when
        auctionListService.list(null, null);

        // then : 필요 없는 왕복을 하지 않는다
        then(auctionListRepository).should(never())
                .findPendingPage(any(), any(), anyLong(), anyInt());
        then(auctionListRepository).should(never())
                .findEndedPage(any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("모자라면 다음 그룹에서 남은 만큼만 이어 읽는다")
    void continuesToNextGroupWithRemainingCount() {
        // given : 진행중 5건뿐이라 16건이 모자란다
        givenLive(liveRows(1, 5));
        givenPending(pendingRows(100, 16));

        // when
        auctionListService.list(null, null);

        // then : 예정에는 21이 아니라 남은 16을 요청해야 페이지가 정확히 21에서 멈춘다
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(auctionListRepository).findPendingPage(any(), any(), anyLong(), limit.capture());
        assertThat(limit.getValue()).isEqualTo(16);
    }

    @Test
    @DisplayName("그룹을 넘어가면 커서를 그 그룹의 시작값으로 되돌린다")
    void resetsCursorWhenMovingToNextGroup() {
        // given : 진행중을 다 읽고 예정과 종료로 넘어간다
        givenLive(liveRows(1, 2));
        givenPending(pendingRows(100, 2));
        givenEnded(endedRows(200, 2));

        // when
        auctionListService.list(null, null);

        // then 1 : 예정은 오름차순이라 아래쪽 끝에서 출발한다
        assertCursorPassedToPending(ASC_START, 0L);

        // then 2 : 종료는 내림차순이라 위쪽 끝에서 출발한다.
        // 앞 그룹의 값을 그대로 넘기면 종료 앞부분이 통째로 잘린다
        assertCursorPassedToEnded(DESC_START, Long.MAX_VALUE);
    }

    // ================= 커서 이어 읽기 =================

    @Test
    @DisplayName("커서가 가리키는 그룹은 처음이 아니라 그 지점부터 읽는다")
    void resumesFromCursorInsteadOfRestarting() {
        // given : 예정 그룹 중간에서 끊긴 커서
        LocalDateTime resumeAt = NOW.plusMinutes(25);
        AuctionListCursor cursor =
                new AuctionListCursor(NOW, AuctionListGroup.PENDING, resumeAt, 42L);

        givenPending(pendingRows(100, 3));
        givenEnded(endedRows(200, 3));

        // when
        auctionListService.list(cursor, null);

        // then 1 : 지나온 진행중은 아예 조회하지 않는다
        then(auctionListRepository).should(never())
                .findLivePage(any(), any(), anyLong(), anyInt());

        // then 2 : 예정에는 커서 값이 그대로 넘어가야 한다.
        // 여기에 시작값이 넘어가면 이전 페이지를 다시 읽어 무한 스크롤이 첫 페이지에 갇힌다
        assertCursorPassedToPending(resumeAt, 42L);
    }

    // ================= 다음 커서 =================

    @Test
    @DisplayName("예정 그룹에서 끝나면 커서에 시작 시각을 담는다")
    void nextCursor_fromPendingCarriesStartTime() {
        // given : 진행중 19건에 예정 2건을 더해 21건. 20번째로 잘리는 행이 예정 77번이다.
        // 77번은 시작 12:25, 마감 12:45
        givenLive(liveRows(1, 19));
        givenPending(List.of(
                pendingRow(77, NOW.plusMinutes(25)),
                pendingRow(78, NOW.plusMinutes(30))));

        // when
        AuctionListInfo info = auctionListService.list(null, null);

        // then : 예정 쿼리는 startTime 으로 정렬하므로 커서도 startTime 이어야 한다.
        // 마감(12:45)이 담기면 다음 페이지에서 시작 12:30~12:45 인 경매가 전부 건너뛰어진다
        AuctionListCursor cursor = info.nextCursor();
        assertThat(cursor.group()).isEqualTo(AuctionListGroup.PENDING);
        assertThat(cursor.sortAt()).isEqualTo(NOW.plusMinutes(25));
        assertThat(cursor.auctionId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("진행중 그룹에서 끝나면 커서에 마감 시각을 담는다")
    void nextCursor_fromLiveCarriesEndTime() {
        // given : 진행중만으로 페이지가 다 찬다
        givenLive(liveRows(1, FETCH_SIZE));

        // when
        AuctionListInfo info = auctionListService.list(null, null);

        // then : 진행중 쿼리는 currentEndTime 으로 정렬한다
        AuctionListRow last = liveRows(1, FETCH_SIZE).get(PAGE_SIZE - 1);
        AuctionListCursor cursor = info.nextCursor();
        assertThat(cursor.group()).isEqualTo(AuctionListGroup.LIVE);
        assertThat(cursor.sortAt()).isEqualTo(last.currentEndTime());
    }

    @Test
    @DisplayName("기준 시각은 페이지를 넘겨도 첫 페이지의 것을 유지한다")
    void nextCursor_keepsSnapshotAt() {
        // given : 첫 페이지보다 이른 시각으로 고정된 커서
        LocalDateTime frozen = NOW.minusMinutes(30);
        AuctionListCursor cursor =
                new AuctionListCursor(frozen, AuctionListGroup.LIVE, ASC_START, 0L);

        givenLive(liveRows(1, FETCH_SIZE));

        // when
        AuctionListInfo info = auctionListService.list(cursor, null);

        // then : 이 값이 흔들리면 그 사이 단계가 바뀐 경매가 자리를 옮겨 커서가 어긋난다
        assertThat(info.nextCursor().snapshotAt()).isEqualTo(frozen);
        then(auctionListRepository).should()
                .findLivePage(eq(frozen), any(), anyLong(), anyInt());
    }

    // ================= 페이지 경계 =================

    @Test
    @DisplayName("한 건 더 읽어 다음 페이지 유무를 판단하고 그 한 건은 버린다")
    void detectsNextPageWithOneExtraRow() {
        // given : 딱 21건
        givenLive(liveRows(1, FETCH_SIZE));

        // when
        AuctionListInfo info = auctionListService.list(null, null);

        // then : 21번째는 판단에만 쓰고 응답에서 뺀다
        assertThat(info.content()).hasSize(PAGE_SIZE);
        assertThat(info.hasNext()).isTrue();
        assertThat(ids(info)).doesNotContain(21L);
    }

    @Test
    @DisplayName("페이지가 덜 차면 다음 커서를 주지 않는다")
    void lastPage_hasNoCursor() {
        // given : 세 그룹을 합쳐도 20건에 못 미친다
        givenLive(liveRows(1, 3));
        givenPending(pendingRows(100, 3));
        givenEnded(endedRows(200, 3));

        // when
        AuctionListInfo info = auctionListService.list(null, null);

        // then
        assertThat(info.content()).hasSize(9);
        assertThat(info.hasNext()).isFalse();
        assertThat(info.nextCursor()).isNull();
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 목록을 준다")
    void emptyResult() {
        // given
        givenLive(List.of());
        givenPending(List.of());
        givenEnded(List.of());

        // when
        AuctionListInfo info = auctionListService.list(null, null);

        // then
        assertThat(info.content()).isEmpty();
        assertThat(info.hasNext()).isFalse();
        assertThat(info.nextCursor()).isNull();
    }

    // ================= 기준 시각 보정 =================

    @Test
    @DisplayName("커서의 기준 시각이 미래면 현재 시각으로 깎는다")
    void clampsFutureSnapshotAt() {
        // given : 조작된 커서. 그대로 믿으면 모든 경매가 종료로 분류된다
        AuctionListCursor tampered =
                new AuctionListCursor(NOW.plusYears(50), AuctionListGroup.LIVE, ASC_START, 0L);

        givenLive(liveRows(1, 3));
        givenPending(List.of());
        givenEnded(List.of());

        // when
        auctionListService.list(tampered, null);

        // then
        then(auctionListRepository).should()
                .findLivePage(eq(NOW), any(), anyLong(), anyInt());
    }

    // ================= 카드 =================

    @Test
    @DisplayName("입찰이 없으면 현재가를 시작가로 채워 내려준다")
    void currentPrice_fallsBackToStartPrice() {
        // given : 현재가가 null 인 진행중 경매
        givenLive(List.of(liveRow(1, NOW.minusMinutes(10))));
        givenPending(List.of());
        givenEnded(List.of());

        // when
        AuctionCardInfo card = auctionListService.list(null, null).content().getFirst();

        // then : null 처리를 화면에 떠넘기지 않는다
        assertThat(card.currentPrice()).isEqualTo(card.startPrice());
        assertThat(card.phase()).isEqualTo(RoomPhase.LIVE);
    }

    @Test
    @DisplayName("닫힌 단계는 접속자를 세지 않는다")
    void closedPhase_hasNoViewerCount() {
        // given : 마감 후 5분이 지난 경매
        givenLive(List.of());
        givenPending(List.of());
        givenEnded(List.of(endedRow(200, NOW.minusHours(2))));

        // when
        AuctionCardInfo card = auctionListService.list(null, null).content().getFirst();

        // then : 경매방도 세지 않는 구간이라 목록만 다른 수를 보이면 안 된다
        assertThat(card.phase()).isEqualTo(RoomPhase.CLOSED);
        assertThat(card.connectedCount()).isZero();
    }

    @Test
    @DisplayName("열린 단계는 경매방의 접속자 수를 그대로 쓴다")
    void openPhase_usesRoomChannel() {
        // given
        givenLive(List.of(liveRow(1, NOW.minusMinutes(10))));
        givenPending(List.of());
        givenEnded(List.of());
        given(roomChannel.countViewers(1L)).willReturn(7);

        // when
        AuctionCardInfo card = auctionListService.list(null, null).content().getFirst();

        // then : 목록 조회는 방 입장이 아니므로 세기만 한다, 구독을 만들 수단이 없어 셀 수도 없다
        assertThat(card.connectedCount()).isEqualTo(7);
    }

    // ================= 상태 필터 =================

    @Test
    @DisplayName("상태 필터가 걸리면 페이지가 모자라도 다음 그룹으로 넘어가지 않는다")
    void doesNotFallThroughToNextGroupWhenFiltered() {
        // given : 진행중이 5건뿐이라 21건에 한참 모자라다
        givenLive(liveRows(1, 5));

        // when
        auctionListService.list(null, AuctionListGroup.LIVE);

        // then : 모자란 만큼 다음 그룹에서 채우면 "진행중" 탭에 예정 경매가 섞여 나온다
        then(auctionListRepository).should(never())
                .findPendingPage(any(), any(), anyLong(), anyInt());
        then(auctionListRepository).should(never())
                .findEndedPage(any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("필터가 가리키는 그룹은 커서가 없어도 그 그룹의 시작값부터 읽는다")
    void startsFromFilteredGroupWithoutCursor() {
        // given
        givenEnded(endedRows(200, 2));

        // when
        auctionListService.list(null, AuctionListGroup.ENDED);

        // then : 진행중부터 훑지 않는다. 종료는 내림차순이라 위쪽 끝에서 출발한다
        then(auctionListRepository).should(never())
                .findLivePage(any(), any(), anyLong(), anyInt());
        assertCursorPassedToEnded(DESC_START, Long.MAX_VALUE);
    }

    // ================= 나의 경매 =================

    @Test
    @DisplayName("나의 경매는 소유자 전용 쿼리로 조회한다")
    void listMineUsesOwnerScopedQueries() {
        // given
        givenMyLive(liveRows(1, FETCH_SIZE));

        // when
        auctionListService.listMine(null, null, SELLER_ID);

        // then : 공개 목록 쿼리를 쓰면 남의 경매까지 섞인다
        then(auctionListRepository).should(never())
                .findLivePage(any(), any(), anyLong(), anyInt());
        verify(auctionListRepository)
                .findMyLivePage(eq(SELLER_ID), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("나의 경매에 상태 필터를 걸면 그 그룹의 소유자 쿼리만 쓴다")
    void listMineWithFilterUsesOnlyThatGroup() {
        // given
        givenMyEnded(endedRows(200, 2));

        // when
        auctionListService.listMine(null, AuctionListGroup.ENDED, SELLER_ID);

        // then
        then(auctionListRepository).should(never())
                .findMyLivePage(anyLong(), any(), any(), anyLong(), any());
        then(auctionListRepository).should(never())
                .findMyPendingPage(anyLong(), any(), any(), anyLong(), any());
        verify(auctionListRepository)
                .findMyEndedPage(eq(SELLER_ID), any(), any(), anyLong(), any());
    }

    // ================= 목 설정 =================

    private void givenMyLive(List<AuctionListRow> rows) {
        given(auctionListRepository.findMyLivePage(anyLong(), any(), any(), anyLong(), any()))
                .willReturn(rows);
    }

    private void givenMyEnded(List<AuctionListRow> rows) {
        given(auctionListRepository.findMyEndedPage(anyLong(), any(), any(), anyLong(), any()))
                .willReturn(rows);
    }

    private void givenLive(List<AuctionListRow> rows) {
        given(auctionListRepository.findLivePage(any(), any(), anyLong(), anyInt())).willReturn(rows);
    }

    private void givenPending(List<AuctionListRow> rows) {
        given(auctionListRepository.findPendingPage(any(), any(), anyLong(), anyInt())).willReturn(rows);
    }

    private void givenEnded(List<AuctionListRow> rows) {
        given(auctionListRepository.findEndedPage(any(), any(), anyLong(), anyInt())).willReturn(rows);
    }

    private void assertCursorPassedToPending(LocalDateTime expectedSortAt, long expectedId) {
        ArgumentCaptor<LocalDateTime> sortAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);

        verify(auctionListRepository)
                .findPendingPage(any(), sortAt.capture(), id.capture(), anyInt());

        assertThat(sortAt.getValue()).isEqualTo(expectedSortAt);
        assertThat(id.getValue()).isEqualTo(expectedId);
    }

    private void assertCursorPassedToEnded(LocalDateTime expectedSortAt, long expectedId) {
        ArgumentCaptor<LocalDateTime> sortAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);

        verify(auctionListRepository)
                .findEndedPage(any(), sortAt.capture(), id.capture(), anyInt());

        assertThat(sortAt.getValue()).isEqualTo(expectedSortAt);
        assertThat(id.getValue()).isEqualTo(expectedId);
    }

    // ================= 행 만들기 =================
    // 마감 = 시작 + 20분, 방 개장 = 시작 - 30분 이라는 도메인 규칙을 그대로 따른다

    private List<AuctionListRow> liveRows(long firstId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> liveRow(firstId + i, NOW.minusMinutes(15 - i % 15)))
                .toList();
    }

    private List<AuctionListRow> pendingRows(long firstId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> pendingRow(firstId + i, NOW.plusMinutes(5L * (i + 1))))
                .toList();
    }

    private List<AuctionListRow> endedRows(long firstId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> endedRow(firstId + i, NOW.minusHours(2).minusMinutes(10L * i)))
                .toList();
    }

    private AuctionListRow liveRow(long id, LocalDateTime start) {
        return row(id, start);
    }

    private AuctionListRow pendingRow(long id, LocalDateTime start) {
        return row(id, start);
    }

    private AuctionListRow endedRow(long id, LocalDateTime start) {
        return row(id, start);
    }

    private AuctionListRow row(long id, LocalDateTime start) {
        return new AuctionListRow(
                id,
                "https://cdn.race.dev/" + id + ".jpg",
                "MODEL-" + id,
                2022,
                35000,
                10_000_000L,
                null,                        // 입찰 전
                start.minusMinutes(30),      // 방 개장
                start,
                start.plusMinutes(20));      // 마감
    }

    private List<Long> ids(AuctionListInfo info) {
        return info.content().stream().map(AuctionCardInfo::auctionId).toList();
    }
}
