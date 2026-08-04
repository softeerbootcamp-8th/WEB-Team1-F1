package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.command.VisitQuoteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 방문견적 신청 요청. 제원은 서버가 번호판으로 재조회하므로 클라이언트는 번호판과 방문 정보만 보낸다.
 * <p>
 * 약관 동의 필드를 받지 않는다. 동의 이력을 남기는 것이 약관 기능의 핵심이고, 검증만 하고 버리는
 * boolean은 그 요구를 만족시키지 못하면서 응답 계약만 굳힌다. 약관은 별도 기능으로 붙인다.
 */
@Schema(description = "방문견적 신청 요청")
public record VisitQuoteRequest(

        @Schema(description = "차량 번호판(공백·대시 없이)", example = "12가3456")
        @NotBlank
        @Pattern(regexp = "^\\d{2,3}[가-힣]\\d{4}$",
                message = "번호판은 공백과 대시 없이 12가3456 형식이어야 합니다.")
        String plateNumber,

        // 로그인했다는 사실이 "이 차가 이 사람 것"을 증명하지는 않는다. 세션은 요청자가 누구인지만
        // 증명하므로, 소유자명을 함께 받아 대조하지 않으면 로그인한 아무나 카탈로그의 임의 번호판으로
        // 평가사 방문을 잡을 수 있다. 시세 조회 단계에서 이미 입력한 값이라 화면에 칸이 새로 늘지 않는다
        @Schema(description = "차량 소유자명. 시세 조회에 입력한 값과 같아야 합니다.", example = "김민수")
        @NotBlank
        @Size(max = VisitQuoteRequest.MAX_OWNER_NAME_LENGTH, message = "소유자명은 50자 이하여야 합니다.")
        String ownerName,

        @Schema(description = "차량이 있는 곳의 주소", example = "서울 성동구 왕십리로 83")
        @NotBlank
        @Size(max = VisitQuoteRequest.MAX_ADDRESS_LENGTH,
                message = "방문 주소는 " + VisitQuoteRequest.MAX_ADDRESS_LENGTH + "자 이하여야 합니다.")
        String visitAddress,

        // @FutureOrPresent를 쓰지 않는다. 그 애너테이션은 서버 기본 시간대의 시스템 시각을 직접 읽어
        // "시각은 주입된 Clock으로만 읽는다"를 우회하고, 고정 Clock 테스트가 과거 날짜 거부를
        // 재현할 수 없게 만든다. 과거 날짜 판정은 Evaluation.request가 today를 받아 처리한다
        @Schema(description = "방문 희망 날짜(오늘 이후)", example = "2026-08-20")
        @NotNull
        LocalDate visitDate,

        // SignUpRequest는 하이픈을 optional로 허용해(^01\d-?\d{3,4}-?\d{4}$) 같은 번호가 두 형식으로
        // 저장되고 있다. 여기서는 화면 안내("'-'를 제외하고 숫자만")대로 숫자만 받아 그 문제를 물려받지 않는다
        @Schema(description = "연락받을 휴대폰 번호(숫자만)", example = "01012345678")
        @NotBlank
        @Pattern(regexp = "^01\\d{8,9}$",
                message = "연락처는 '-' 없이 숫자만 입력해야 합니다.")
        String contactPhone
) {

    /** 컬럼 길이가 아니라 입력 상한이다. 도로명 주소에 상세주소를 붙여도 남는 폭으로 잡았다 */
    static final int MAX_ADDRESS_LENGTH = 200;

    /** QuoteRequest와 같은 값이어야 한다. 두 API가 같은 카탈로그의 같은 컬럼을 대조한다 */
    static final int MAX_OWNER_NAME_LENGTH = 50;

    /**
     * 인증 주체를 인자로 받는다. Command는 유스케이스 입력 전체이고 행위 주체도 그 입력의 일부다.
     * <p>
     * 소유자명과 주소만 trim한다. 소유자명은 QuoteRequest와 같은 이유로(대조 키라 앞뒤 공백이
     * 섞이면 조회가 실패한다) 다듬고, 번호판과 연락처는 정규식이 공백을 이미 막고 있다.
     */
    public VisitQuoteCommand toCommand(long sellerId) {
        return new VisitQuoteCommand(sellerId, plateNumber, ownerName.trim(),
                visitAddress.trim(), visitDate, contactPhone);
    }
}
