package com.softeer.race.progress.domain;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.evaluation.domain.EvaluationStatus;

/**
 * 판매자가 낸 차 한 대가 지금 어디까지 왔는지
 * <p>
 * <b>저장하지 않는다.</b> 이 단계는 {@link EvaluationStatus} · {@link AuctionStatus} · 경매글 삭제
 * 여부가 이미 들고 있는 사실을 한 값으로 접은 것이라, 컬럼으로 따로 두면 원본이 바뀔 때마다
 * 같이 고쳐야 하고 빠뜨리면 두 값이 어긋난다. 어긋난 쪽이 화면에 보이는 값이라 발견도 늦다.
 * <p>
 * 지금 서비스에는 판매 신청과 방문견적 신청 두 경로가 병행한다. 판매 신청은 평가를 만들지 않고
 * 곧바로 경매까지 만들어 {@code AUCTION_*}부터 시작하고, 방문견적은 경매를 만들지 않아
 * {@code EVALUATION_*}에서 멈춘다. 두 경로가 하나로 합쳐지면 한 차량이 평가 단계부터 경매 단계까지
 * 이어서 지나가고, 그때 이 enum은 고치지 않아도 된다.
 */
public enum ProgressStage {

    /** 방문 평가를 신청했고 아직 평가사가 정해지지 않았다 */
    EVALUATION_REQUESTED,

    /** 평가사가 정해졌고 방문을 기다린다 */
    EVALUATION_ASSIGNED,

    /** 평가가 반려됐다. 더 진행되지 않으며 같은 차로 다시 신청할 수 있다 */
    EVALUATION_REJECTED,

    /** 평가는 승인됐지만 아직 경매글로 올리지 않았다 */
    LISTING_PENDING,

    /** 경매가 예약됐고 아직 시작 전이다 */
    AUCTION_SCHEDULED,

    /** 경매가 진행 중이다 */
    AUCTION_LIVE,

    /** 낙찰됐다 */
    AUCTION_WON,

    /** 입찰이 한 건도 없이 끝났다 */
    AUCTION_FAILED,

    /** 끝난 경매의 경매글을 판매자가 내렸다 */
    LISTING_REMOVED;

    /**
     * 경매 쪽을 평가 쪽보다 먼저 본다. 둘 다 있는 차량은 평가를 이미 통과했다는 뜻이라 뒤쪽이
     * 더 진행된 사실이고, 화면이 알려야 하는 것은 마지막으로 도달한 지점이다.
     *
     * @throws IllegalStateException 평가도 경매도 없는 차량일 때. 조회가 그런 행을 걸러내므로
     *                               도달하면 쿼리와 이 판정이 어긋났다는 뜻이고, 사용자가 고칠 수
     *                               있는 문제가 아니라 서버 결함이라 BusinessException이 아니다
     */
    public static ProgressStage of(EvaluationStatus evaluationStatus, boolean evaluatorAssigned,
                                   AuctionStatus auctionStatus, boolean listingRemoved) {
        if (auctionStatus != null) {
            return ofAuction(auctionStatus, listingRemoved);
        }

        if (evaluationStatus != null) {
            return ofEvaluation(evaluationStatus, evaluatorAssigned);
        }

        throw new IllegalStateException("평가도 경매도 없는 차량이 진행 상황으로 넘어왔다");
    }

    // 내림은 종료된 경매에만 허용되므로(AuctionService.delete) 진행 중인 경매를 가릴 일이 없다.
    // 그래도 상태보다 먼저 보는 이유는, 내린 뒤에는 낙찰이든 유찰이든 판매자가 할 일이 없어
    // 결과보다 "내렸음"이 지금 상태를 더 정확히 설명하기 때문이다
    private static ProgressStage ofAuction(AuctionStatus auctionStatus, boolean listingRemoved) {
        if (listingRemoved) {
            return LISTING_REMOVED;
        }

        return switch (auctionStatus) {
            case SCHEDULED -> AUCTION_SCHEDULED;
            case IN_PROGRESS -> AUCTION_LIVE;
            case ENDED -> AUCTION_WON;
            case FAILED -> AUCTION_FAILED;
        };
    }

    // 배정 여부가 상태가 아니라 evaluator 필드의 null로 표현된다(Evaluation 주석 참고).
    // 그래서 REQUESTED 하나가 여기서 두 단계로 갈린다
    private static ProgressStage ofEvaluation(EvaluationStatus evaluationStatus, boolean evaluatorAssigned) {
        return switch (evaluationStatus) {
            case REQUESTED -> evaluatorAssigned ? EVALUATION_ASSIGNED : EVALUATION_REQUESTED;
            case APPROVED -> LISTING_PENDING;
            case REJECTED -> EVALUATION_REJECTED;
        };
    }
}
