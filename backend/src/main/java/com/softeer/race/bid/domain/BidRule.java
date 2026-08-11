package com.softeer.race.bid.domain;

import com.softeer.race.common.exception.BusinessException;

import static com.softeer.race.bid.exception.BidErrorCode.BID_AMOUNT_NOT_ALIGNED;
import static com.softeer.race.bid.exception.BidErrorCode.BID_AMOUNT_TOO_LOW;

/**
 * 지금 이 경매에 낼 수 있는 금액의 기준
 *
 * @param currentPrice 배수 계산의 기준점. 첫 입찰이면 시작가
 * @param increment    현재가가 속한 구간의 최저 상승가
 * @param minAmount    지금 낼 수 있는 최소 금액
 */
public record BidRule(
        long currentPrice,
        long increment,
        long minAmount
) {
    public BidRule {
        // 사용자가 고칠 수 없는 서버 데이터 파손이라 BusinessException을 쓰지 않는다.
        if (increment <= 0) {
            throw new IllegalStateException("상승가는 0보다 커야 한다. 값 %d".formatted(increment));
        }

        if (minAmount < currentPrice) {
            throw new IllegalStateException(
                    "최소 금액 %d가 현재가 %d보다 낮다.".formatted(minAmount, currentPrice));
        }
    }

    /**
     * 성립 가능한 금액인지 판정한다.
     * 거절 사유만 내려가므로, 다시 낼 금액은 클라이언트가 방 현황을 다시 받아 계산한다.
     *
     * @param amount 클라이언트가 보내는 입찰가
     */
    public void validate(long amount) {
        // 순서가 규칙의 일부다. 두 조건을 다 어긴 금액이 어느 사유로 거절되는지가 순서로 정해진다.

        // 하한이 먼저인 이유는 그게 실제 원인이기 때문이다. +버튼을 덜 누른 사용자에게
        // "단위가 안 맞습니다"를 돌려주면 무엇을 고쳐야 하는지 알 수 없다.
        validateMinimum(amount);

        if ((amount - currentPrice) % increment != 0) {
            throw new BusinessException(BID_AMOUNT_NOT_ALIGNED);
        }
    }

    /**
     * 하한만 본다. 현재가가 낡아도 결과가 뒤집히지 않아 잠금 앞에서 미리 부를 수 있다.
     * 정렬 검사는 현재가가 기준점이라 그렇게 못 한다.
     */
    public void validateMinimum(long amount) {
        if (amount < minAmount) {
            throw new BusinessException(BID_AMOUNT_TOO_LOW);
        }
    }
}