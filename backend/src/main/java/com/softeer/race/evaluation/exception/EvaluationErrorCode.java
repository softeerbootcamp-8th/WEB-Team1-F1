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

    NOT_FOUND(HttpStatus.NOT_FOUND, "평가 정보를 찾을 수 없습니다."),

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
     * 진단서는 APPROVED에도 붙는다. 합치면 승인된 신청에 진단서를 못 붙이거나, 이미 배정된 신청이
     * 다시 배정 가능해진다.
     */
    NOT_DIAGNOSABLE(HttpStatus.CONFLICT, "이미 종료된 평가에는 진단서를 등록할 수 없습니다."),

    DIAGNOSTIC_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 진단서가 없습니다.");

    private final HttpStatus status;
    private final String message;

    EvaluationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. SellErrorCode와 AuctionErrorCode에도 VEHICLE_NOT_FOUND가 있어,
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
