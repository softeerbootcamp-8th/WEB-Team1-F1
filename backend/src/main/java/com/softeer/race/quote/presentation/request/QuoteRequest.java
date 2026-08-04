package com.softeer.race.quote.presentation.request;

import com.softeer.race.quote.application.dto.command.QuoteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 시세 조회 요청.
 * <p>
 * 번호판 정규화를 조회 구현체에 넣지 않는 대신 여기서 형식을 강제한다. 구현체마다 정규화 규칙을
 * 다시 구현하면 두 규칙이 어긋나는 순간 조회 키가 갈라진다. 판매 신청과 같은 패턴을 쓴다.
 * <p>
 * 소유자명은 형식을 강제하지 않는다. 외국인 이름이나 성 없는 이름을 정규식으로 막을 근거가 없어서
 * 길이 상한만 두고 앞뒤 공백만 다듬는다.
 * <p>
 * 주행거리는 조회기가 아니라 여기서 받는다. 시점에 따라 변하는 값이라 원장에 두면 언제 측정한
 * 값인지 알 수 없는 수치가 시세에 반영된다. 신고값이라 위조할 수 있고, 그 대가로 얻는 것은
 * "지금 내 차"의 시세다 — 실측 교정은 방문견적에서 평가사가 한다.
 */
@Schema(description = "시세 조회 요청")
public record QuoteRequest(

        @Schema(description = "차량 번호판(공백·대시 없이)", example = "12가3456")
        @NotBlank
        @Pattern(regexp = "^\\d{2,3}[가-힣]\\d{4}$",
                message = "번호판은 공백과 대시 없이 12가3456 형식이어야 합니다.")
        String plateNumber,

        @Schema(description = "차량 소유자명", example = "김민수")
        @NotBlank
        @Size(max = 50, message = "소유자명은 50자 이하여야 합니다.")
        String ownerName,

        // Integer 다. 원시 int 로 두면 필드를 아예 빼고 보낸 요청이 400 이 아니라 주행거리 0km 로
        // 조용히 통과해, 감가가 전혀 없는 최고가 시세를 받아 간다
        @Schema(description = "현재 주행거리(km)", example = "45000")
        @NotNull
        @Min(value = 0, message = "주행거리는 0km 이상이어야 합니다.")
        @Max(value = QuoteRequest.MAX_MILEAGE_KM, message = "주행거리는 999,999 km 이하여야 합니다.")
        Integer mileage
) {

    /** 상용차 폐차 직전까지도 넘기기 어려운 값이다. 상한 자체가 목적이 아니라 오타(4500000)를 잡는 그물이다 */
    static final int MAX_MILEAGE_KM = 999_999;

    public QuoteCommand toCommand() {
        return new QuoteCommand(plateNumber, ownerName.trim(), mileage);
    }
}
