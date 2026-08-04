package com.softeer.race.evaluation.domain;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.vehicle.domain.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("평가 요청")
class EvaluationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

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

    // 중복 접수 차단과 재신청 허용이 둘 다 이 집합에 달려 있다
    @Test
    @DisplayName("진행 중 상태는 REQUESTED와 APPROVED뿐이고 REJECTED는 종료 상태다")
    void inProgressStatuses() {
        assertThat(EvaluationStatus.inProgress())
                .containsExactlyInAnyOrder(EvaluationStatus.REQUESTED, EvaluationStatus.APPROVED)
                .doesNotContain(EvaluationStatus.REJECTED);
    }
}
