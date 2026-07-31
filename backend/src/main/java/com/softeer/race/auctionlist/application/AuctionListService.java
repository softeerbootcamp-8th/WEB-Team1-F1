package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.application.dto.AuctionListCursor;
import com.softeer.race.auctionlist.application.dto.AuctionListInfo;
import com.softeer.race.auctionlist.domain.AuctionListGroup;
import com.softeer.race.auctionlist.domain.AuctionListRepository;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.auctionpost.domain.PostStatus;
import com.softeer.race.auctionroom.domain.AuctionRoomSnapshot;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.auctionroom.application.RoomChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionListService {

    // 그리드 한 페이지에 보이는 카드 수.
    private static final int PAGE_SIZE = 20;

    private final AuctionListRepository auctionListRepository;
    private final RoomChannel roomChannel;
    private final Clock clock;

    /**
     * 목록 한 페이지, 커서가 없으면 첫 페이지로 본다.
     */
    public AuctionListInfo list(AuctionListCursor cursor) {
        LocalDateTime now = LocalDateTime.now(clock);
        AuctionListCursor start = (cursor != null) ? cursor : AuctionListCursor.first(now);

        // 클라이언트가 돌려보내는 값이라 미래 시각이 올 수 있다. 그대로 믿으면 모든 경매가 종료로 분류된다.
        LocalDateTime snapshotAt = start.snapshotAt().isAfter(now) ? now : start.snapshotAt();

        // 한 건 더 읽어 다음 페이지 유무를 판단한다. 전체를 세는 쿼리를 피하려는 것이다.
        List<Positioned> found = collect(start, snapshotAt, PAGE_SIZE + 1);

        boolean hasNext = found.size() > PAGE_SIZE;
        List<Positioned> page = hasNext ? found.subList(0, PAGE_SIZE) : found;

        List<AuctionCardInfo> content = page.stream()
                .map(positioned -> toCard(positioned.row(), now))
                .toList();

        AuctionListCursor next = hasNext ? nextCursor(snapshotAt, page.getLast()) : null;

        return new AuctionListInfo(content, now, hasNext, next);
    }

    /**
     * 커서가 가리키는 그룹부터 채우고, 모자란 만큼 다음 그룹에서 이어 읽는다.
     */
    private List<Positioned> collect(AuctionListCursor cursor, LocalDateTime snapshotAt, int need) {
        List<Positioned> found = new ArrayList<>();
        int remaining = need;

        for (AuctionListGroup group : AuctionListGroup.startingFrom(cursor.group())) {
            // 커서가 있던 그룹만 이어 읽고, 그 뒤 그룹은 처음부터 읽는다.
            // 시작 값이 그룹마다 다르다. 종료는 내림차순이라 위쪽 끝에서 출발한다.
            boolean resuming = (group == cursor.group());
            LocalDateTime from = resuming ? cursor.sortAt() : group.startSortAt();
            long fromId = resuming ? cursor.auctionId() : group.startAuctionId();

            List<AuctionListRow> rows = query(group, snapshotAt, from, fromId, remaining);
            for (AuctionListRow row : rows) {
                found.add(new Positioned(group, row));
            }

            remaining -= rows.size();
            if (remaining <= 0) {
                break;
            }
        }

        return found;
    }

    private List<AuctionListRow> query(AuctionListGroup group, LocalDateTime snapshotAt,
                                       LocalDateTime cursorSortAt, long cursorAuctionId, int need) {
        Limit limit = Limit.of(need);

        return switch (group) {
            case LIVE -> auctionListRepository.findLivePage(
                    PostStatus.PUBLISHED, snapshotAt, cursorSortAt, cursorAuctionId, limit);
            case PENDING -> auctionListRepository.findPendingPage(
                    PostStatus.PUBLISHED, snapshotAt, cursorSortAt, cursorAuctionId, limit);
            case ENDED -> auctionListRepository.findEndedPage(
                    PostStatus.PUBLISHED, snapshotAt, cursorSortAt, cursorAuctionId, limit);
        };
    }

    private AuctionCardInfo toCard(AuctionListRow row, LocalDateTime now) {
        // 단계 판정은 경매방과 한 벌을 쓴다. 복제하면 같은 경매가 두 화면에서 다른 단계로 보일 수 있다.
        AuctionRoomSnapshot snapshot = new AuctionRoomSnapshot(
                row.startPrice(), row.currentPrice(),
                row.roomOpenAt(), row.startTime(), row.currentEndTime());

        // 정렬은 snapshotAt 기준이지만 단계는 지금 시각으로 잰다. 깊은 페이지에서도 배지는 맞아야 한다.
        RoomPhase phase = snapshot.phaseAt(now);

        // 닫힌 단계는 경매방도 접속자를 세지 않는다. 목록만 다른 수를 보이면 안 된다.
        int connectedCount = phase.allowsConnection()
                ? roomChannel.countSubscribers(row.auctionId()) : 0;

        return new AuctionCardInfo(
                row.auctionId(),
                phase,
                row.thumbnailUrl(),
                row.model(),
                row.modelYear(),
                row.mileage(),
                row.startPrice(),
                snapshot.displayPrice(),
                row.roomOpenAt(),
                row.startTime(),
                row.currentEndTime(),
                connectedCount);
    }

    private AuctionListCursor nextCursor(LocalDateTime snapshotAt, Positioned last) {
        return new AuctionListCursor(
                snapshotAt,                              // 기준 시각은 페이지를 넘겨도 유지한다
                last.group(),
                last.group().sortAtOf(last.row()),       // 그룹마다 정렬 기준 컬럼이 다르다
                last.row().auctionId());
    }

    // 어느 그룹에서 읽힌 행인지 기억해야 커서를 만들 수 있다.
    private record Positioned(AuctionListGroup group, AuctionListRow row) {
    }
}
