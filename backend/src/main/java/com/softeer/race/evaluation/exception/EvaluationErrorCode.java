package com.softeer.race.evaluation.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EvaluationErrorCode implements ErrorCode {

    /**
     * 미등록 번호판과 소유자명 불일치를 같은 코드로 번역한다. 포트가 둘을 구분해 주지 않아 여기서
     * 갈라놓을 방법이 없고, 그게 의도다 — 구분되면 번호판을 바꿔 넣어보며 소유자명을 역추적할 수 있다.
     */
    VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "차량 정보를 찾을 수 없습니다. 번호판과 이름을 확인해 주세요."),

    /**
     * 정상 흐름에서는 발생하지 않는다. user_session이 users를 FK로 참조하고 인터셉터가 방금 세션을
     * 검증했으므로 계정이 없을 수 없다. 그래도 두는 이유는 이 코드가 없으면 그 상황이
     * GlobalExceptionHandler의 최후 방어선을 타고 500 INTERNAL_ERROR가 되기 때문이다.
     */
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "판매자 정보를 찾을 수 없습니다."),

    /**
     * 400이 아니라 409다. 요청 자체는 형식과 값이 모두 올바르고, 거부되는 이유는 서버가 들고 있는
     * 현재 상태(진행 중인 신청이 이미 있음)뿐이다. 클라이언트가 값을 고쳐 다시 보낼 수 있는
     * 400과 구별돼야 화면이 "입력을 확인하세요"가 아니라 "이미 신청됨"을 안내할 수 있다.
     */
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 진행 중인 방문견적 신청이 있습니다."),

    /**
     * 요청 DTO의 {@code @FutureOrPresent}로 막지 않고 여기까지 끌고 온다. 그 애너테이션은 서버 기본
     * 시간대의 시스템 시각을 직접 읽어, 주입된 Clock으로만 시각을 읽는다는 규칙을 우회한다.
     * 그러면 Clock을 고정한 테스트가 과거 날짜 거부를 재현할 수 없다.
     */
    PAST_VISIT_DATE(HttpStatus.BAD_REQUEST, "방문 희망 날짜는 오늘 이후여야 합니다."),

    /**
     * 배정하려는 신청이 없다. 목록에서 고른 id라도 그 사이 신청이 사라질 수 있어 정상 흐름에서도 난다.
     * <p>
     * 진단서 첨부·조회도 이 코드를 쓴다. 두 흐름 모두 "지정한 방문견적 신청이 없다"는 같은 상황이라
     * 코드를 나눌 이유가 없다.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "방문견적 신청을 찾을 수 없습니다."),

    /**
     * SELLER_NOT_FOUND와 같은 이유로 둔다 — 인터셉터가 방금 세션을 검증했으므로 정상 흐름에서는
     * 발생하지 않지만, 코드가 없으면 그 상황이 500 INTERNAL_ERROR가 된다.
     */
    EVALUATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "배정받을 회원 정보를 찾을 수 없습니다."),

    /**
     * 먼저 수락한 평가사가 이미 있다. 이 흐름의 "최초 1명" 규칙이 실제로 작동하는 지점이라
     * 정상 흐름에서 나는 응답이다 — 목록을 보던 사이 다른 평가사가 먼저 수락하면 여기로 온다.
     */
    ALREADY_ASSIGNED(HttpStatus.CONFLICT, "이미 다른 평가사가 배정된 신청입니다."),

    /**
     * 평가가 이미 끝난(승인·반려) 신청이다. ALREADY_ASSIGNED와 갈라 두는 이유는 화면이 안내할 말이
     * 다르기 때문이다 — 이쪽은 목록을 다시 봐도 그 건이 돌아오지 않는다.
     */
    NOT_ASSIGNABLE(HttpStatus.CONFLICT, "배정할 수 있는 상태의 신청이 아닙니다."),

    /**
     * 우리가 발급하지 않았거나, 발급했더라도 문서가 아닌 주소다. 후자를 구분하지 않는 이유는
     * VehicleErrorCode.UNMANAGED_IMAGE_URL과 같다 — 클라이언트가 할 일이 "발급받은 문서 주소를
     * 다시 보낸다"로 같고, 구분해 주면 어떤 키가 존재하는지 되물어 확인하는 통로가 된다.
     */
    UNMANAGED_DOCUMENT_URL(HttpStatus.BAD_REQUEST,
            "이 서비스에서 발급한 문서 주소가 아닙니다. 업로드 주소 발급 API가 돌려준 값을 그대로 보내야 합니다."),

    /**
     * 400이 아니라 409다. 요청 자체는 올바르고 거부되는 이유는 서버가 들고 있는 상태(이미 반려됨)뿐이다.
     * <p>
     * NOT_ASSIGNABLE과 갈라 둔다. 두 코드가 보는 상태 집합이 다르다 — 배정은 REQUESTED만 받고,
     * 진단서는 재제출 때문에 APPROVED에도 붙는다. 합치면 결과를 고쳐 다시 올릴 수 없거나, 이미
     * 배정된 신청이 다시 배정 가능해진다.
     */
    NOT_DIAGNOSABLE(HttpStatus.CONFLICT, "이미 종료된 평가에는 진단서를 등록할 수 없습니다."),

    /**
     * 아직 결과가 한 번도 제출되지 않은 평가에 항목별 수정을 시도했다.
     * <p>
     * <b>이 코드가 부분 수정의 존재 근거다.</b> 결과 제출을 조각내지 않은 이유가 "주행거리가 빈 차가
     * 경매로 넘어가지 않게"인데({@code EvaluationResultService}), 항목별 수정은 바로 그 조각난
     * 입구다. 이미 완전하게 제출된 결과에서만 출발하게 막아야 한 칸을 바꿔도 반쪽짜리 차량이
     * 나오지 않는다 — 즉 이 관문이 없으면 부분 수정 자체가 그 불변식을 깬다.
     * <p>
     * NOT_DIAGNOSABLE과 갈라 둔다. 저쪽은 "끝나 버린 평가"이고 이쪽은 "아직 시작하지 않은 평가"라
     * 화면이 안내할 말이 정반대다 — 이쪽은 결과를 먼저 제출하면 풀린다.
     */
    RESULT_NOT_SUBMITTED(HttpStatus.CONFLICT,
            "아직 제출된 평가 결과가 없습니다. 결과를 먼저 제출해야 항목별로 수정할 수 있습니다."),

    /**
     * 아직 아무도 수락하지 않은 신청이다. 403이 아니라 409인 이유는 <b>요청자가 누구든 같은 답</b>이기
     * 때문이다 — 권한이 모자란 것이 아니라 담당자를 정하는 단계를 아직 지나지 않았다.
     * <p>
     * NOT_ASSIGNED_EVALUATOR와 갈라 두는 것은 화면이 안내할 말이 달라서다. 이쪽은 배정 대기
     * 목록에서 수락하면 풀리고, 저쪽은 수락해도 풀리지 않는다(이미 임자가 있다).
     */
    EVALUATOR_NOT_ASSIGNED(HttpStatus.CONFLICT, "아직 담당 평가사가 정해지지 않은 신청입니다."),

    /**
     * 다른 평가사가 담당인 신청에 방문 결과를 내려 했다. 승인 제출과 반려가 함께 쓴다.
     * <p>
     * 평가사 역할은 공통 인가가 먼저 확인하고, 이 코드는 평가사이지만 이 건의 담당자가 아닌 경우를
     * 구분한다 — 배정은 대기 목록에서 수락해야 받는다.
     */
    NOT_ASSIGNED_EVALUATOR(HttpStatus.FORBIDDEN, "이 신청에 배정된 평가사만 방문 결과를 등록할 수 있습니다."),

    /**
     * 승인 또는 반려로 이미 끝난 신청을 반려하려 했다. 400이 아니라 409인 이유는 DUPLICATE_REQUEST와
     * 같다 — 요청 자체는 올바르고, 거부되는 이유는 서버가 들고 있는 현재 상태뿐이다.
     * <p>
     * NOT_DIAGNOSABLE과 갈라 둔다. 보는 상태 집합이 다르다 — 진단서는 재제출 때문에 APPROVED에도
     * 붙지만, 반려는 REQUESTED만 받는다. 합치면 승인된 신청을 반려로 뒤집을 수 있게 된다.
     * <p>
     * APPROVED와 REJECTED를 한 코드로 묶는다. NOT_ASSIGNABLE과 ALREADY_ASSIGNED를 갈라 둔 것과
     * 기준이 같다 — 거기서는 한쪽만 목록에서 다시 볼 수 있어 화면이 할 말이 달랐지만, 여기서는
     * 두 경우 모두 평가사의 담당 목록에서 이미 끝난 건이라 안내가 같다.
     */
    NOT_REJECTABLE(HttpStatus.CONFLICT, "반려할 수 있는 상태의 신청이 아닙니다.");

    private final HttpStatus status;
    private final String message;

    EvaluationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. AuctionErrorCode와 VehicleErrorCode에도 VEHICLE_NOT_FOUND가 있어,
     * 접두사가 없으면 서로 다른 원인이 프론트에서 구별 불가능한 같은 문자열이 된다.
     */
    @Override
    public String code() {
        return "EVALUATION_" + name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
