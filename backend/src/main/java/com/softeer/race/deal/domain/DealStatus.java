package com.softeer.race.deal.domain;

/**
 * 거래 단계
 * <p>
 * 단계마다 기한과 귀책이 달라서, 뭉쳐 두면 어떤 기한을 넘겼는지 상태에서 읽히지 않는다.
 * 화면도 같은 목록을 쓴다.
 */
public enum DealStatus {

    // 구매자를 기다리는 상태. 거래 첫 시작 신호
    DEPOSIT_PENDING,
    // 판매자 기다리는 상태. 서류 대기
    DOCUMENT_PENDING,

    // 잔금보다 먼저 받는다, 언제 차가 오는지 모른 채 큰 돈을 보내는 순서가 되지 않게
    TRANSPORT_PENDING,

    // 구매자 기다리며 되돌릴 수 있는 마지막 단계
    BALANCE_PENDING,
    // 구매자 기다린다. 잔금 받았으니 취소가 아니라 반품의 영역
    HANDOVER_PENDING,

    // 사람이 아니라 시스템을 기다린다, 돈이 실제로 나가는 유일한 실패 지점이라 단계로 남긴다
    SETTLING,

    COMPLETED,
    CANCELLED;

    /**
     * 정상 진행으로 갈 수 있는지
     * <p>
     * 취소는 여기서 참이 되지 않는다. 이 판정을 통과한 전이는 사유를 받지 않아서,
     * 표에 넣으면 사유 없는 취소가 성립한다.
     */
    public boolean canTransitionTo(DealStatus target) {
        // 목적지를 받아야 어긋난 순서와 재요청이 걸린다, "다음으로"만으로는 그냥 밀린다
        return switch (this) {
            case DEPOSIT_PENDING -> target == DOCUMENT_PENDING;
            case DOCUMENT_PENDING -> target == TRANSPORT_PENDING;
            case TRANSPORT_PENDING -> target == BALANCE_PENDING;
            case BALANCE_PENDING -> target == HANDOVER_PENDING;
            case HANDOVER_PENDING -> target == SETTLING;
            case SETTLING -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * 취소할 수 있는지
     * <p>
     * 잔금이 확인된 뒤로는 닫는다. 되돌리려면 환불·재탁송·명의 원복이 따라와서 취소와 다른 흐름이다.
     */
    public boolean isCancellable() {
        return switch (this) {
            case DEPOSIT_PENDING, DOCUMENT_PENDING, TRANSPORT_PENDING, BALANCE_PENDING -> true;
            case HANDOVER_PENDING, SETTLING, COMPLETED, CANCELLED -> false;
        };
    }
}