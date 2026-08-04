package com.softeer.race.sell.presentation.request;

import com.softeer.race.sell.application.dto.command.SellApplicationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 판매 신청 요청. 제원은 서버가 번호판으로 재조회하고, 주행거리만 클라이언트가 신고한다.
 * <p>
 * <b>주행거리는 검증되지 않는다.</b> 이 경로는 평가사 방문을 거치지 않고 곧바로 경매가 되므로,
 * 낮게 신고하면 부풀려진 시작가가 그대로 경매 목록에 오른다. 방문견적(/api/visit-quotes)은
 * 평가사 실측으로 교정되지만 여기는 그 단계가 없다. VehicleLookup 의 TODO 대로 이 경로가
 * 방문견적에 흡수되면 함께 사라지는 문제다.
 * <p>
 * 번호판 정규화를 조회 구현체에 넣지 않는 대신 여기서 형식을 강제한다. 공백·대시가 섞인 값은
 * 애초에 들어올 수 없어야 저장된 번호판과 조회 키가 갈라지지 않는다.
 */
@Schema(description = "판매 신청 요청")
public record SellApplicationRequest(

        @Schema(description = "차량 번호판(공백·대시 없이)", example = "12가3456")
        @NotBlank
        @Pattern(regexp = "^\\d{2,3}[가-힣]\\d{4}$",
                message = "번호판은 공백과 대시 없이 12가3456 형식이어야 합니다.")
        String plateNumber,

        // Integer 다. 원시 int 로 두면 필드를 빼고 보낸 요청이 400 이 아니라 주행거리 0km 로
        // 조용히 통과해, 감가가 전혀 없는 시세가 그대로 저장된다
        @Schema(description = "현재 주행거리(km)", example = "45000")
        @NotNull
        @Min(value = 0, message = "주행거리는 0km 이상이어야 합니다.")
        @Max(value = SellApplicationRequest.MAX_MILEAGE_KM, message = "주행거리는 999,999km 이하여야 합니다.")
        Integer mileage
) {

    /** 오타(4500000)를 잡는 그물이다. QuoteRequest와 같은 값이어야 한다 — 시세 조회를 통과한 값이 여기서 막히면 안 된다 */
    static final int MAX_MILEAGE_KM = 999_999;

    /**
     * 인증 주체를 인자로 받는다. Command는 유스케이스 입력 전체이고 행위 주체도 그 입력의 일부다.
     * 서비스를 1-인자로 유지하면 입력 필드가 늘어도 컨트롤러↔서비스 시그니처가 바뀌지 않는다.
     * (기존 무인자 toCommand()들은 둘 다 미인증 엔드포인트라 주체가 없었을 뿐이다.)
     */
    public SellApplicationCommand toCommand(long sellerId) {
        return new SellApplicationCommand(sellerId, plateNumber, mileage);
    }
}
