package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.EvaluationAssignmentInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 평가사 배정 서비스
 * <p>
 * mock 을 만드는 헬퍼 호출을 {@code given(...)} 인수 안에 넣지 않는다. 스터빙 도중에 다른 스터빙이
 * 시작되면 Mockito 가 미완료 스터빙으로 보고 UnfinishedStubbingException 을 던진다.
 * 그래서 모든 시나리오가 대상을 먼저 지역 변수에 담는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("평가사 배정 서비스")
class EvaluationAssignmentServiceTest {

    private static final long EVALUATOR_ID = 500L;
    private static final long EVALUATION_ID = 510L;
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 8, 20);
    private static final String PLATE_NUMBER = "12가3456";
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EvaluationAssignmentService evaluationAssignmentService;

    @Test
    @DisplayName("평가사가 수락하면 담당으로 확정되고 판매자 연락처가 함께 나간다")
    void assign() {
        User evaluator = userWithRole(Role.EVALUATOR);
        given(evaluator.getId()).willReturn(EVALUATOR_ID);
        Evaluation evaluation = requestedWithPlateNumber();
        given(userRepository.findById(EVALUATOR_ID)).willReturn(Optional.of(evaluator));
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));

        EvaluationAssignmentInfo info =
                evaluationAssignmentService.assign(EVALUATION_ID, EVALUATOR_ID);

        assertThat(evaluation.getEvaluator()).isSameAs(evaluator);
        assertThat(info.contactPhone()).isEqualTo(CONTACT_PHONE);
        assertThat(info.plateNumber()).isEqualTo(PLATE_NUMBER);
        assertThat(info.status()).isEqualTo(EvaluationStatus.REQUESTED.name());

        // 저장 호출이 없다. 잠금과 함께 읽어 온 영속 엔티티라 커밋 시점에 변경이 반영된다
        then(evaluationRepository).should(never()).save(evaluation);
    }

    @Test
    @DisplayName("평가사는 자기 차량의 신청을 직접 수락할 수 없다")
    void assignRejectsSelfAssignment() {
        User evaluator = userWithRole(Role.EVALUATOR);
        given(evaluator.getId()).willReturn(EVALUATOR_ID);
        Evaluation evaluation = requestedOwnedByEvaluator();
        given(userRepository.findById(EVALUATOR_ID)).willReturn(Optional.of(evaluator));
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));

        assertThatThrownBy(() -> evaluationAssignmentService.assign(EVALUATION_ID, EVALUATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.SELF_ASSIGNMENT_NOT_ALLOWED));

        assertThat(evaluation.getEvaluator()).isNull();
    }

    @Test
    @DisplayName("없는 회원의 세션이면 EVALUATOR_NOT_FOUND로 거부한다")
    void assignRejectsMissingEvaluator() {
        given(userRepository.findById(EVALUATOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationAssignmentService.assign(EVALUATION_ID, EVALUATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.EVALUATOR_NOT_FOUND));
    }

    @Test
    @DisplayName("없는 신청은 NOT_FOUND로 거부한다")
    void assignRejectsMissingEvaluation() {
        User evaluator = userWithRole(Role.EVALUATOR);
        given(userRepository.findById(EVALUATOR_ID)).willReturn(Optional.of(evaluator));
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationAssignmentService.assign(EVALUATION_ID, EVALUATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("배정 대기 목록은 REQUESTED 상태로만 조회한다")
    void findAssignable() {
        List<Evaluation> waiting = List.of(requestedWithPlateNumber());
        given(evaluationRepository.findAssignable(EvaluationStatus.REQUESTED)).willReturn(waiting);

        assertThat(evaluationAssignmentService.findAssignable())
                .singleElement()
                .satisfies(info -> {
                    assertThat(info.plateNumber()).isEqualTo(PLATE_NUMBER);
                    assertThat(info.visitDate()).isEqualTo(VISIT_DATE);
                    assertThat(info.visitAddress()).isEqualTo(VISIT_ADDRESS);
                });
    }

    // 역할은 스터빙하지 않는다. 서비스가 읽지 않으므로 두면 UnnecessaryStubbingException 이 된다.
    // 인자로는 남겨 둔다 — 어떤 역할로 시나리오를 세웠는지가 테스트에서 읽혀야 한다
    private User userWithRole(Role role) {
        return mock(User.class, role.name());
    }

    // 방문일 규칙을 통과해야 하므로 오늘을 방문일보다 앞선 날짜로 넘긴다
    private Evaluation requestedWithPlateNumber() {
        Vehicle vehicle = mock(Vehicle.class);
        given(vehicle.getPlateNumber()).willReturn(PLATE_NUMBER);

        return Evaluation.request(
                vehicle, VISIT_DATE, VISIT_ADDRESS, CONTACT_PHONE, VISIT_DATE.minusDays(16));
    }

    private Evaluation requestedOwnedByEvaluator() {
        Vehicle vehicle = mock(Vehicle.class);
        given(vehicle.isOwnedBy(EVALUATOR_ID)).willReturn(true);

        return Evaluation.request(
                vehicle, VISIT_DATE, VISIT_ADDRESS, CONTACT_PHONE, VISIT_DATE.minusDays(16));
    }

}
