package com.softeer.race.auctionlist.presentation.request;

import com.softeer.race.auctionlist.application.dto.AuctionListCursor;
import com.softeer.race.auctionlist.domain.AuctionListGroup;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 직전 응답의 nextCursor를 그대로 돌려받는다.
 */
public record AuctionListCursorRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime snapshotAt,

        Integer sortPriority,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime sortAt,

        Long auctionId,

        // null이면 전체, 있으면 해당 그룹만
        AuctionListGroup filter
) {

    // 일부만 온 커서로는 이어 읽을 지점을 특정할 수 없다.
    // 조용히 첫 페이지로 돌리면 무한 스크롤이 처음으로 되감겨 더 헷갈린다.
    @AssertTrue(message = "커서 정보는 전부 있거나 전부 없어야 합니다.")
    boolean isCursorConsistent() {
        return isEmpty() || isComplete();
    }

    // 알 수 없는 순번이면 어느 그룹부터 읽을지 정할 수 없다.
    @AssertTrue(message = "커서의 그룹 순번이 올바르지 않습니다.")
    boolean isGroupOrderValid() {
        return sortPriority == null || AuctionListGroup.isValidOrder(sortPriority);
    }

    // 필터가 걸리면 그 그룹만 읽으므로, 다른 그룹 커서는 이어 읽을 지점이 되지 못하고 버려진다.
    // 탭을 옮기며 이전 커서를 그대로 보낸 경우라 조용히 첫 페이지를 주면 화면이 되감긴다.
    @AssertTrue(message = "커서의 그룹이 필터와 일치해야 합니다.")
    boolean isCursorGroupMatchingFilter() {
        // 커서 없음·필터 없음·잘못된 순번은 각각 다른 검증이 잡는다
        if (filter == null || sortPriority == null || !AuctionListGroup.isValidOrder(sortPriority)) {
            return true;
        }
        return AuctionListGroup.ofOrder(sortPriority) == filter;
    }

    /**
     * 커서가 없으면 null을 준다. 첫 페이지라는 뜻이다.
     */
    public AuctionListCursor toCursor() {
        return isEmpty() ? null
                : new AuctionListCursor(snapshotAt, AuctionListGroup.ofOrder(sortPriority), sortAt, auctionId);
    }

    // 첫 페이지
    private boolean isEmpty() {
        return snapshotAt == null && sortPriority == null && sortAt == null && auctionId == null;
    }

    private boolean isComplete() {
        return snapshotAt != null && sortPriority != null && sortAt != null && auctionId != null;
    }
}
