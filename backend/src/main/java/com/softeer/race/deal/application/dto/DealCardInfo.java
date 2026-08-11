package com.softeer.race.deal.application.dto;

import com.softeer.race.deal.domain.DealListRow;
import com.softeer.race.deal.domain.DealSide;

/**
 * 거래 목록 카드 한 건, 조회 결과에 "이 거래에서 내 쪽"을 붙인 것
 * <p>
 * 조회 결과를 다시 펼치지 않고 그대로 담는다. 알림 목록이 서비스가 더할 값이 없어 한 겹을 안 둔 것과
 * 반대로, 거래는 조회한 사람이 누구냐에 따라 달라지는 값이 하나 있어 겹이 필요하다.
 */
public record DealCardInfo(DealListRow card, DealSide mySide, long viewerId) {

    public static DealCardInfo of(DealListRow card, long viewerId) {
        return new DealCardInfo(card, card.sideOf(viewerId), viewerId);
    }
}
