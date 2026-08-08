package com.softeer.race.deal.domain;

/**
 * 거래가 취소된 이유
 * <p>
 * 사유가 귀책을 안다. 기한 초과로 생기는 취소와 사용자가 고르는 취소가 같은 목록을 써야
 * 같은 상황에 보증금이 다르게 가는 일이 없다.
 */
public enum CancellationReason {

    DEPOSIT_TIMEOUT(FaultParty.BUYER),
    DOCUMENT_TIMEOUT(FaultParty.SELLER),
    TRANSPORT_TIMEOUT(FaultParty.SELLER),
    BALANCE_TIMEOUT(FaultParty.BUYER);

    private final FaultParty faultParty;

    CancellationReason(FaultParty faultParty) {
        this.faultParty = faultParty;
    }

    public FaultParty faultParty() {
        return faultParty;
    }
}