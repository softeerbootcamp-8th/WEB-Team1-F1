package com.softeer.race.progress.domain;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매자 진행 상황 쿼리를 실제 MySQL 에서
 * <p>
 * 1. 두 경로 합치기
 * 평가만 있는 차량과 경매만 있는 차량이 한 목록에 함께 나오는지
 * <p>
 * 2. 단계
 * 조인 결과에서 단계가 갈리는지, 내려간 경매글이 결과보다 앞서는지
 * <p>
 * 3. 매핑
 * select new 가 네 테이블 값을 레코드에 채우는지, 비어 있는 쪽이 null 로 담기는지
 * <p>
 * 4. 소유자
 * 남의 차량이 목록과 상세 어디에도 새지 않는지
 */
@DisplayName("판매자 진행 상황 조회 쿼리 테스트")
@Transactional
@Sql("/sql/progress-fixture.sql")
class SellerProgressRepositoryTest extends IntegrationTestSupport {

    private static final long SELLER_ID = 200L;
    private static final long OTHER_SELLER_ID = 201L;

    @Autowired
    private SellerProgressRepository sellerProgressRepository;

    @Test
    @DisplayName("평가만 있는 차량과 경매만 있는 차량이 한 목록에 나온다")
    void list_mergesBothPaths() {
        // when
        List<SellerProgressRow> rows = sellerProgressRepository.findAllBySeller(SELLER_ID);

        // then : 210 은 평가도 경매글도 없어 빠지고, 211 은 남의 차량이라 빠진다
        assertThat(rows).extracting(SellerProgressRow::vehicleId)
                .containsExactly(209L, 208L, 207L, 206L, 205L, 204L, 203L, 202L, 201L);
    }

    @Test
    @DisplayName("차량마다 단계가 갈린다")
    void list_derivesStagePerVehicle() {
        // when
        List<SellerProgressRow> rows = sellerProgressRepository.findAllBySeller(SELLER_ID);

        // then : 최근 신청 순이라 209 부터 내려온다
        assertThat(rows).extracting(SellerProgressRow::stage)
                .containsExactly(
                        ProgressStage.LISTING_REMOVED,
                        ProgressStage.AUCTION_FAILED,
                        ProgressStage.AUCTION_WON,
                        ProgressStage.AUCTION_LIVE,
                        ProgressStage.AUCTION_SCHEDULED,
                        ProgressStage.LISTING_PENDING,
                        ProgressStage.EVALUATION_REJECTED,
                        ProgressStage.EVALUATION_ASSIGNED,
                        ProgressStage.EVALUATION_REQUESTED);
    }

    @Test
    @DisplayName("평가 단계 차량은 경매 값이 비어 있다")
    void list_leavesAuctionColumnsNull() {
        // when
        SellerProgressRow row = findOne(201L);

        // then : left join 이 아니면 이 차량 자체가 목록에서 사라진다
        assertThat(row.auctionId()).isNull();
        assertThat(row.startPrice()).isNull();
        assertThat(row.currentPrice()).isNull();
        assertThat(row.evaluationStatus()).isNotNull();
    }

    @Test
    @DisplayName("판매 신청으로 들어온 차량은 평가 값이 비어 있다")
    void list_leavesEvaluationColumnsNull() {
        // when
        SellerProgressRow row = findOne(205L);

        // then
        assertThat(row.evaluationStatus()).isNull();
        assertThat(row.evaluatorId()).isNull();
        assertThat(row.visitDate()).isNull();
        assertThat(row.auctionId()).isEqualTo(205L);
    }

    @Test
    @DisplayName("배정 여부는 평가사 식별자의 유무로 담긴다")
    void list_carriesEvaluatorAssignment() {
        assertThat(findOne(201L).evaluatorId()).isNull();
        assertThat(findOne(202L).evaluatorId()).isEqualTo(210L);
    }

    @Test
    @DisplayName("반려 사유는 반려된 건에만 담긴다")
    void list_carriesRejectReason() {
        assertThat(findOne(203L).rejectReason()).isEqualTo("침수 이력이 확인됩니다.");
        assertThat(findOne(202L).rejectReason()).isNull();
    }

    @Test
    @DisplayName("입찰이 없던 경매는 현재가가 null 로 담긴다")
    void list_carriesNullCurrentPrice() {
        // when
        SellerProgressRow row = findOne(208L);

        // then : 시작가는 원시 long 이지만 현재가는 입찰 전이면 비어 있다
        assertThat(row.startPrice()).isEqualTo(25_000_000L);
        assertThat(row.currentPrice()).isNull();
    }

    @Test
    @DisplayName("내려간 경매글도 목록에서 사라지지 않는다")
    void list_keepsRemovedListing() {
        // when
        SellerProgressRow row = findOne(209L);

        // then : 조인 조건으로 걸러냈다면 이 차량은 평가도 없어 통째로 빠진다
        assertThat(row.stage()).isEqualTo(ProgressStage.LISTING_REMOVED);
        assertThat(row.listingRemovedAt()).isNotNull();
    }

    @Test
    @DisplayName("상세는 내 차량만 찾는다")
    void detail_findsOwnVehicle() {
        // when
        Optional<SellerProgressRow> found = sellerProgressRepository.findOneBySeller(SELLER_ID, 207L);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().stage()).isEqualTo(ProgressStage.AUCTION_WON);
        assertThat(found.get().currentPrice()).isEqualTo(23_000_000L);
    }

    @Test
    @DisplayName("남의 차량은 상세에서 비어 있다")
    void detail_hidesOtherSellersVehicle() {
        // when : 211 은 판매자 201 의 차량이다
        Optional<SellerProgressRow> found = sellerProgressRepository.findOneBySeller(SELLER_ID, 211L);

        // then
        assertThat(found).isEmpty();
        assertThat(sellerProgressRepository.findOneBySeller(OTHER_SELLER_ID, 211L)).isPresent();
    }

    @Test
    @DisplayName("평가도 경매글도 없는 차량은 상세에서도 비어 있다")
    void detail_skipsVehicleWithoutProgress() {
        assertThat(sellerProgressRepository.findOneBySeller(SELLER_ID, 210L)).isEmpty();
    }

    private SellerProgressRow findOne(long vehicleId) {
        return sellerProgressRepository.findOneBySeller(SELLER_ID, vehicleId).orElseThrow();
    }
}
