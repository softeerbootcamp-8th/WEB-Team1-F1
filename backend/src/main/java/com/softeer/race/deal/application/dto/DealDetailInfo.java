package com.softeer.race.deal.application.dto;

import com.softeer.race.deal.domain.DealDetailRow;
import com.softeer.race.deal.domain.DealSide;

import java.time.LocalDateTime;

/** 거래 상세, 조회 결과에 "이 거래에서 내 쪽"과 서버 시각을 붙인 것 */
public record DealDetailInfo(DealDetailRow detail, DealSide mySide, LocalDateTime serverTime) {

    public static DealDetailInfo of(DealDetailRow detail, long viewerId, LocalDateTime serverTime) {
        return new DealDetailInfo(detail, detail.sideOf(viewerId), serverTime);
    }
}
