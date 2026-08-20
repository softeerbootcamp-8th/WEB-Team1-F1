package com.softeer.race.deal.domain;

/**
 * 거래 단계
 * <p>
 * 서비스가 하는 일은 만날 약속을 잡아 주는 데까지다. 대금을 보관하지 않고 명의이전을 확인할
 * 수단도 없어서, 실제 인도 이후는 상태로 두지 않는다.
 * <p>
 * 단계마다 움직일 수 있는 사람이 정확히 한 명이다. 그래서 상태 하나가 "지금 누구를 기다리는가"를
 * 그대로 답할 수 있고, 권한 검사와 화면의 "내 차례" 표시가 같은 판정을 쓴다.
 */
public enum DealStatus {

    // 구매자가 거래 의사를 확인하는 자리. 실제 결제는 하지 않는다
    BUYER_CONFIRM_PENDING,

    // 판매자가 명의이전 서류와 탁송 일정을 낸다. 같은 사람이 하는 일이라 나누지 않는다
    SELLER_SUBMIT_PENDING,

    // 구매자가 판매자 일정에 동의하고 자기 차량 인수 일정을 잡는다. 동의만 한 상태를 따로 두지 않는 이유는
    // 그 상태에서도 기다리는 사람이 여전히 구매자라, 아무도 움직이지 않는 통과 상태가 되기 때문이다
    BUYER_SCHEDULE_PENDING,

    // 약속이 정해졌다. 여기서부터 차량과 대금은 당사자끼리 만나서 주고받는다.
    // COMPLETED 라 하지 않은 이유 — 인도가 아직 미래인데 완료라고 쓰면 화면이 거짓말을 한다
    CONFIRMED,

    CANCELLED;

    /**
     * 지금 이 거래가 기다리는 쪽, 끝난 거래는 기다릴 사람이 없어 비어 있다
     */
    public DealSide waitingFor() {
        return switch (this) {
            case BUYER_CONFIRM_PENDING, BUYER_SCHEDULE_PENDING -> DealSide.BUYER;
            case SELLER_SUBMIT_PENDING -> DealSide.SELLER;
            case CONFIRMED, CANCELLED -> null;
        };
    }

    /**
     * 정상 진행으로 갈 수 있는지
     * <p>
     * 취소는 여기서 참이 되지 않는다. 이 판정을 통과한 전이는 사유를 받지 않아서,
     * 표에 넣으면 사유 없는 취소가 성립한다.
     */
    public boolean canTransitionTo(DealStatus target) {
        // 목적지를 받아야 어긋난 순서와 재요청이 걸린다, "다음으로"만으로는 그냥 밀린다
        return switch (this) {
            case BUYER_CONFIRM_PENDING -> target == SELLER_SUBMIT_PENDING;
            case SELLER_SUBMIT_PENDING -> target == BUYER_SCHEDULE_PENDING;
            case BUYER_SCHEDULE_PENDING -> target == CONFIRMED;
            case CONFIRMED, CANCELLED -> false;
        };
    }

    /**
     * 취소할 수 있는지
     * <p>
     * 약속이 잡힌 뒤로는 닫는다. 그때부터는 서비스가 아니라 서로 연락해서 정할 일이다.
     */
    public boolean isCancellable() {
        return switch (this) {
            case BUYER_CONFIRM_PENDING, SELLER_SUBMIT_PENDING, BUYER_SCHEDULE_PENDING -> true;
            case CONFIRMED, CANCELLED -> false;
        };
    }
}
