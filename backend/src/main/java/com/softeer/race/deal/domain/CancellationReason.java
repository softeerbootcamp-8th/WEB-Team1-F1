package com.softeer.race.deal.domain;

/**
 * 거래가 취소된 이유
 * <p>
 * 사유가 귀책을 안다. 기한 초과로 생기는 취소가 나중에 붙어도 같은 목록을 써야,
 * 같은 상황에 귀책이 다르게 잡히는 일이 없다.
 */
public enum CancellationReason {

    BUYER_CANCELLED(FaultParty.BUYER),
    SELLER_CANCELLED(FaultParty.SELLER);

    private final FaultParty faultParty;

    CancellationReason(FaultParty faultParty) {
        this.faultParty = faultParty;
    }

    public FaultParty faultParty() {
        return faultParty;
    }
}