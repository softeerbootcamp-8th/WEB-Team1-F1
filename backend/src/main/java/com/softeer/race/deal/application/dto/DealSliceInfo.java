package com.softeer.race.deal.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 거래 목록 한 페이지 */
public record DealSliceInfo(
        List<DealCardInfo> content,
        LocalDateTime serverTime,
        boolean hasNext,
        Long nextCursor
) {
}