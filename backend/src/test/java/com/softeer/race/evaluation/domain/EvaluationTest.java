package com.softeer.race.evaluation.domain;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.domain.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("평가 요청")
class EvaluationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

    private static final long SELLER_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long OTHER_EVALUATOR_ID = 602L;
    private static final long STRANGER_ID = 603L;

    @Test
    @DisplayName("접수된 신청은 REQUESTED 상태이고 평가사가 배정되지 않는다")
    void request() {
        Vehicle vehicle = mock(Vehicle.class);

        Evaluation evaluation = Evaluation.request(
                vehicle, TODAY.plusDays(16), VISIT_ADDRESS, CONTACT_PHONE, TODAY);

        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.REQUESTED);
        // 배정 대기를 별도 상태가 아니라 evaluator의 null로 표현하기로 한 결정을 고정한다
        assertThat(evaluation.getEvaluator()).isNull();
        assertThat(evaluation.getRejectReason()).isNull();

        assertThat(evaluation.getVehicle()).isSameAs(vehicle);
        assertThat(evaluation.getVisitDate()).isEqualTo(TODAY.plusDays(16));
        assertThat(evaluation.getVisitAddress()).isEqualTo(VISIT_ADDRESS);
        assertThat(evaluation.getContactPhone()).isEqualTo(CONTACT_PHONE);
    }

    // 경계다. isBefore가 아니라 isAfter나 !isEqual로 잘못 쓰면 여기서만 깨진다
    @Test
    @DisplayName("오늘 날짜는 방문 희망일로 허용한다")
    void requestAllowsToday() {
        assertThatCode(() -> Evaluation.request(
                mock(Vehicle.class), TODAY, VISIT_ADDRESS, CONTACT_PHONE, TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("어제 날짜는 PAST_VISIT_DATE로 거부한다")
    void requestRejectsPastDate() {
        assertThatThrownBy(() -> Evaluation.request(
                mock(Vehicle.class), TODAY.minusDays(1), VISIT_ADDRESS, CONTACT_PHONE, TODAY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.PAST_VISIT_DATE));
    }

    // 중복 접수 차단과 재신청 허용이 둘 다 이 집합에 달려 있다.
    // APPROVED가 들어 있는 것은 진단이 끝나도 출품 동의가 남아 흐름이 계속되기 때문이고,
    // 빠지면 진단을 마친 차를 다시 방문 신청할 수 있게 된다
    @Test
    @DisplayName("REJECTED만 종료 상태이고 나머지는 전부 진행 중이다")
    void inProgressStatuses() {
        assertThat(EvaluationStatus.inProgress())
                .containsExactlyInAnyOrder(EvaluationStatus.REQUESTED, EvaluationStatus.APPROVED)
                .doesNotContain(EvaluationStatus.REJECTED);
    }

    @Test
    @DisplayName("수락한 평가사가 담당으로 확정되고 상태는 REQUESTED로 남는다")
    void assignTo() {
        Evaluation evaluation = requested();
        User evaluator = mock(User.class);

        evaluation.assignTo(evaluator);

        assertThat(evaluation.getEvaluator()).isSameAs(evaluator);
        // 배정과 평가 결과가 다른 축이라는 결정을 고정한다.
        // 여기서 상태를 바꾸면 "배정됨"과 "평가 승인"이 같은 필드를 다투게 된다
        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.REQUESTED);
    }

    /**
     * "최초 수락 1명"이 실제로 지켜지는 지점. 배정된 뒤의 수락은 앞의 배정을 덮어쓰지 않는다.
     * <p>
     * 두 요청이 동시에 도착할 때 이 검사만으로는 부족하다. 둘 다 배정 전 상태를 읽고 통과할 수 있어
     * 잠금이 필요하고, 그 근거는 {@code EvaluationAssignmentIntegrationTest}가 고정한다.
     */
    @Test
    @DisplayName("이미 배정된 신청은 ALREADY_ASSIGNED로 거부한다")
    void assignToRejectsAlreadyAssigned() {
        Evaluation evaluation = requested();
        User first = mock(User.class);
        evaluation.assignTo(first);

        assertThatThrownBy(() -> evaluation.assignTo(mock(User.class)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.ALREADY_ASSIGNED));

        assertThat(evaluation.getEvaluator()).isSameAs(first);
    }

    @Test
    @DisplayName("배정된 평가사는 진단 결과를 붙일 수 있다")
    void validateDiagnosableBy() {
        // given
        Evaluation evaluation = requested();
        evaluation.assignTo(evaluator(EVALUATOR_ID));

        // when & then : 반려된 신청 거부(NOT_DIAGNOSABLE)는 REJECTED로 만드는 공개 경로가 없어
        //               픽스처로 상태를 심는 통합테스트가 맡는다
        assertThatCode(() -> evaluation.validateDiagnosableBy(EVALUATOR_ID))
                .doesNotThrowAnyException();
    }

    /**
     * 진단서를 붙일 자격을 배정으로만 판정하기로 한 결정을 고정한다. 역할을 함께 보지 않으므로
     * 배정되지 않은 사람은 평가사여도 여기서 떨어진다 — 자격을 보는 자리는 배정하는 곳 한 군데다.
     */
    @Test
    @DisplayName("다른 평가사의 담당이면 NOT_ASSIGNED_EVALUATOR")
    void validateDiagnosableByRejectsOtherEvaluator() {
        // given
        Evaluation evaluation = requested();
        evaluation.assignTo(evaluator(EVALUATOR_ID));

        // when & then
        assertThatThrownBy(() -> evaluation.validateDiagnosableBy(OTHER_EVALUATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.NOT_ASSIGNED_EVALUATOR));
    }

    // 403이 아니라 409다. 권한이 모자란 것이 아니라 담당자를 정하는 단계를 지나지 않았다 —
    // 요청자가 누구든 답이 같고, 대기 목록에서 수락하면 풀린다.
    // null 검사를 빠뜨리면 여기서 NPE가 난다
    @Test
    @DisplayName("아직 배정 전이면 누구에게도 EVALUATOR_NOT_ASSIGNED")
    void validateDiagnosableByRejectsUnassigned() {
        assertThatThrownBy(() -> requested().validateDiagnosableBy(EVALUATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.EVALUATOR_NOT_ASSIGNED));
    }

    @Test
    @DisplayName("결과가 제출되면 APPROVED가 되고 담당자는 그대로다")
    void approve() {
        // given
        Evaluation evaluation = requested();
        User evaluator = evaluator(EVALUATOR_ID);
        evaluation.assignTo(evaluator);

        // when
        evaluation.approve();

        // then : 제출이 배정을 건드리면 "먼저 수락한 한 명"을 우회하는 통로가 생긴다
        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.APPROVED);
        assertThat(evaluation.getEvaluator()).isSameAs(evaluator);
    }

    // 재제출은 결과를 갈아 끼우는 것이지 상태를 되돌리거나 새로 만드는 것이 아니다
    @Test
    @DisplayName("이미 APPROVED인 평가에 다시 제출해도 상태는 그대로다")
    void approveIsIdempotent() {
        Evaluation evaluation = requested();
        evaluation.assignTo(evaluator(EVALUATOR_ID));
        evaluation.approve();

        evaluation.approve();

        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.APPROVED);
    }

    // 제출한 평가사가 결과를 고치러 다시 오는 흐름이다
    @Test
    @DisplayName("APPROVED인 평가에도 담당 평가사는 결과를 다시 제출할 수 있다")
    void validateDiagnosableByAllowsResubmit() {
        Evaluation evaluation = requested();
        evaluation.assignTo(evaluator(EVALUATOR_ID));
        evaluation.approve();

        assertThatCode(() -> evaluation.validateDiagnosableBy(EVALUATOR_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("신청한 판매자와 배정된 평가사는 상세를 볼 수 있다")
    void isViewableBy() {
        // given
        Evaluation evaluation = sellerOwned(SELLER_ID);
        evaluation.assignTo(evaluator(EVALUATOR_ID));

        // when & then
        assertThat(evaluation.isViewableBy(SELLER_ID)).isTrue();
        assertThat(evaluation.isViewableBy(EVALUATOR_ID)).isTrue();
    }

    // 배정 전에는 evaluator가 null이다. null 검사를 빠뜨리면 여기서 NPE가 난다
    @Test
    @DisplayName("무관한 회원은 볼 수 없고, 배정 전이어도 NPE가 나지 않는다")
    void isViewableByRejectsStranger() {
        assertThat(sellerOwned(SELLER_ID).isViewableBy(STRANGER_ID)).isFalse();
    }

    private static Evaluation sellerOwned(long sellerId) {
        Vehicle vehicle = mock(Vehicle.class);
        User seller = mock(User.class);
        given(vehicle.getSeller()).willReturn(seller);
        given(seller.getId()).willReturn(sellerId);

        return Evaluation.request(vehicle, TODAY.plusDays(16), VISIT_ADDRESS, CONTACT_PHONE, TODAY);
    }

    private static User evaluator(long id) {
        User evaluator = mock(User.class);
        given(evaluator.getId()).willReturn(id);

        return evaluator;
    }

    // 평가가 끝난 신청(NOT_ASSIGNABLE)은 여기서 만들 수 없다. status를 바꾸는 방법이 아직 없어
    // 리플렉션 없이는 그 상태에 도달하지 못하므로, 그 경로는 픽스처로 상태를 심는 통합테스트가 맡는다
    private Evaluation requested() {
        return Evaluation.request(
                mock(Vehicle.class), TODAY.plusDays(16), VISIT_ADDRESS, CONTACT_PHONE, TODAY);
    }
}
