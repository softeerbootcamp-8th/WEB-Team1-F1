package com.softeer.race.progress.domain;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 단계 판정
 * <p>
 * 저장된 값 하나로 정해지지 않는 자리를 본다. 배정 여부는 상태가 아니라 evaluator 의 null 로
 * 표현되고, 경매와 평가가 함께 있으면 어느 쪽이 이겨야 하는지도 여기서 갈린다.
 */
@DisplayName("진행 단계 판정 테스트")
class ProgressStageTest {

    @Test
    @DisplayName("평가사가 배정되지 않은 신청은 배정 대기다")
    void requested_withoutEvaluator() {
        ProgressStage stage = ProgressStage.of(EvaluationStatus.REQUESTED, false, null, false);

        assertThat(stage).isEqualTo(ProgressStage.EVALUATION_REQUESTED);
    }

    @Test
    @DisplayName("같은 REQUESTED 라도 평가사가 정해졌으면 방문 대기다")
    void requested_withEvaluator() {
        ProgressStage stage = ProgressStage.of(EvaluationStatus.REQUESTED, true, null, false);

        assertThat(stage).isEqualTo(ProgressStage.EVALUATION_ASSIGNED);
    }

    @Test
    @DisplayName("승인됐지만 아직 경매가 없으면 출품 대기다")
    void approved_withoutAuction() {
        ProgressStage stage = ProgressStage.of(EvaluationStatus.APPROVED, true, null, false);

        assertThat(stage).isEqualTo(ProgressStage.LISTING_PENDING);
    }

    @Test
    @DisplayName("반려는 그대로 반려다")
    void rejected() {
        ProgressStage stage = ProgressStage.of(EvaluationStatus.REJECTED, true, null, false);

        assertThat(stage).isEqualTo(ProgressStage.EVALUATION_REJECTED);
    }

    @Test
    @DisplayName("평가가 없어도 경매만으로 단계가 정해진다")
    void auctionOnly() {
        // 판매 신청으로 들어온 차량이다, 평가 행이 아예 없다
        ProgressStage stage = ProgressStage.of(null, false, AuctionStatus.SCHEDULED, false);

        assertThat(stage).isEqualTo(ProgressStage.AUCTION_SCHEDULED);
    }

    @Test
    @DisplayName("평가와 경매가 함께 있으면 더 진행된 경매 쪽을 보여준다")
    void auctionWinsOverEvaluation() {
        ProgressStage stage = ProgressStage.of(EvaluationStatus.APPROVED, true, AuctionStatus.IN_PROGRESS, false);

        assertThat(stage).isEqualTo(ProgressStage.AUCTION_LIVE);
    }

    @Test
    @DisplayName("입찰이 있었으면 낙찰, 없었으면 유찰이다")
    void endedOrFailed() {
        assertThat(ProgressStage.of(null, false, AuctionStatus.ENDED, false))
                .isEqualTo(ProgressStage.AUCTION_WON);
        assertThat(ProgressStage.of(null, false, AuctionStatus.FAILED, false))
                .isEqualTo(ProgressStage.AUCTION_FAILED);
    }

    @Test
    @DisplayName("경매글을 내렸으면 낙찰이었더라도 내렸음이 먼저다")
    void listingRemoved_winsOverResult() {
        ProgressStage stage = ProgressStage.of(null, false, AuctionStatus.ENDED, true);

        assertThat(stage).isEqualTo(ProgressStage.LISTING_REMOVED);
    }

    @Test
    @DisplayName("평가도 경매도 없으면 조회가 걸러냈어야 할 행이라 서버 결함으로 터진다")
    void neither_isServerFault() {
        assertThatThrownBy(() -> ProgressStage.of(null, false, null, false))
                .isInstanceOf(IllegalStateException.class);
    }
}
