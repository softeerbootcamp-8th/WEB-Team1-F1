package com.softeer.race.user.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 사용 중인 휴대전화 번호입니다."),
    UNSUPPORTED_SIGNUP_ROLE(HttpStatus.BAD_REQUEST, "해당 유형으로는 회원가입할 수 없습니다."),
    // 사원증 자체에 대한 판정은 DealerApplicationErrorCode 로 옮겼다. 여기 남은 것은 가입 요청이
    // 앞뒤가 맞는지에 대한 것이라, 사원증을 낼 수 없는 유형인지는 심사 도메인이 알 일이 아니다
    DEALER_LICENSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "일반 회원가입에는 자동차매매사원증을 등록할 수 없습니다."),
    INVALID_REAL_NAME(HttpStatus.BAD_REQUEST, "이름은 두 글자 이상이어야 합니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    // 관리자와 평가사가 여기 걸린다. 관리자를 막는 것이 곧 자기 자신을 정지할 수 없다는 보장이다
    NOT_SUSPENDABLE_ROLE(HttpStatus.BAD_REQUEST, "일반 회원과 딜러만 이용을 정지할 수 있습니다."),
    // 관리자 둘이 같은 목록을 열어 두고 차례로 눌렀을 때, 뒤에 누른 사유가 앞의 사유를 덮지 않게 한다
    ALREADY_SUSPENDED(HttpStatus.CONFLICT, "이미 이용이 정지된 회원입니다."),
    ALREADY_ACTIVE(HttpStatus.CONFLICT, "이용이 정지되지 않은 회원입니다.");

    private final HttpStatus status;
    private final String message;

    UserErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return "USER_" + name();
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
