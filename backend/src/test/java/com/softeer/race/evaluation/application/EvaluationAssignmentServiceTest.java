package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import com.softeer.race.evaluation.application.dto.info.AssignableEvaluationsInfo;
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
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    // 서비스의 PAGE_SIZE 와 같은 값. 경계를 확인하려면 테스트도 그 수를 알아야 한다
    private static final int PAGE_SIZE = 20;

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
    @DisplayName("커서가 없으면 어떤 신청보다도 앞선 자리에서 첫 페이지를 읽는다")
    void findAssignableStartsFromFirstCursor() {
        List<Evaluation> waiting = List.of(requestedWithPlateNumber());
        given(evaluationRepository.findAssignable(
                eq(EvaluationStatus.REQUESTED), eq(LocalDate.EPOCH), eq(0L), any(Limit.class)))
                .willReturn(waiting);

        AssignableEvaluationsInfo info = evaluationAssignmentService.findAssignable(null);

        assertThat(info.hasNext()).isFalse();
        assertThat(info.nextCursor()).isNull();
        assertThat(info.content())
                .singleElement()
                .satisfies(evaluation -> {
                    assertThat(evaluation.plateNumber()).isEqualTo(PLATE_NUMBER);
                    assertThat(evaluation.visitDate()).isEqualTo(VISIT_DATE);
                    assertThat(evaluation.visitAddress()).isEqualTo(VISIT_ADDRESS);
                });
    }

    @Test
    @DisplayName("커서를 받으면 그 자리 다음부터 읽는다")
    void findAssignableResumesFromCursor() {
        AssignableEvaluationCursor cursor = new AssignableEvaluationCursor(VISIT_DATE, EVALUATION_ID);
        given(evaluationRepository.findAssignable(
                eq(EvaluationStatus.REQUESTED), eq(VISIT_DATE), eq(EVALUATION_ID), any(Limit.class)))
                .willReturn(List.of());

        AssignableEvaluationsInfo info = evaluationAssignmentService.findAssignable(cursor);

        assertThat(info.content()).isEmpty();
        assertThat(info.hasNext()).isFalse();
    }

    @Test
    @DisplayName("한 건을 더 읽어 다음 페이지가 있는지 보고, 그 한 건은 돌려주지 않는다")
    void findAssignableTrimsProbeRow() {
        // 페이지 크기 + 1 건. 마지막 한 건은 다음 페이지가 있는지 보려고 읽은 것이라 응답에 담기지 않는다
        List<Evaluation> found = waitingList(PAGE_SIZE + 1);
        given(evaluationRepository.findAssignable(
                eq(EvaluationStatus.REQUESTED), eq(LocalDate.EPOCH), eq(0L), any(Limit.class)))
                .willReturn(found);

        AssignableEvaluationsInfo info = evaluationAssignmentService.findAssignable(null);

        assertThat(info.content()).hasSize(PAGE_SIZE);
        assertThat(info.hasNext()).isTrue();

        // 커서는 돌려준 마지막 항목이다. 읽고 버린 한 건을 가리키면 그 신청이 통째로 건너뛰어진다
        assertThat(info.nextCursor())
                .isEqualTo(new AssignableEvaluationCursor(VISIT_DATE.plusDays(PAGE_SIZE - 1), PAGE_SIZE));
    }

    @Test
    @DisplayName("딱 페이지 크기만큼이면 다음 페이지가 없다")
    void findAssignableEndsWhenExactlyPageSize() {
        List<Evaluation> found = waitingList(PAGE_SIZE);
        given(evaluationRepository.findAssignable(
                eq(EvaluationStatus.REQUESTED), eq(LocalDate.EPOCH), eq(0L), any(Limit.class)))
                .willReturn(found);

        AssignableEvaluationsInfo info = evaluationAssignmentService.findAssignable(null);

        assertThat(info.content()).hasSize(PAGE_SIZE);
        assertThat(info.hasNext()).isFalse();
        assertThat(info.nextCursor()).isNull();
    }

    @Test
    @DisplayName("배정 대기 건수는 REQUESTED 상태로만 센다")
    void countAssignable() {
        given(evaluationRepository.countAssignable(EvaluationStatus.REQUESTED)).willReturn(42L);

        assertThat(evaluationAssignmentService.countAssignable()).isEqualTo(42L);
    }

    // 역할은 스터빙하지 않는다. 서비스가 읽지 않으므로 두면 UnnecessaryStubbingException 이 된다.
    // 인자로는 남겨 둔다 — 어떤 역할로 시나리오를 세웠는지가 테스트에서 읽혀야 한다
    private User userWithRole(Role role) {
        return mock(User.class, role.name());
    }

    // 방문일이 하루씩 밀리는 대기 신청들. id 는 1 부터 매겨 커서가 가리키는 자리를 확인할 수 있게 한다
    private List<Evaluation> waitingList(int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(order -> waiting(order, VISIT_DATE.plusDays(order - 1)))
                .toList();
    }

    // 커서는 id 로 동률을 가르는데, 영속되지 않은 엔티티는 id 가 비어 있어 직접 넣어 준다
    private Evaluation waiting(long id, LocalDate visitDate) {
        Evaluation evaluation = Evaluation.request(
                mock(Vehicle.class), visitDate, VISIT_ADDRESS, CONTACT_PHONE, visitDate.minusDays(16));
        ReflectionTestUtils.setField(evaluation, "id", id);

        return evaluation;
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
