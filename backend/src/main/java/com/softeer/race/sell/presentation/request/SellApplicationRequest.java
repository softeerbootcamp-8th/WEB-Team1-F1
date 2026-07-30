package com.softeer.race.sell.presentation.request;

import com.softeer.race.sell.application.dto.command.SellApplicationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 판매 신청 요청. 제원은 서버가 번호판으로 재조회하므로 클라이언트는 번호판만 보낸다.
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
        String plateNumber
) {

    /**
     * 인증 주체를 인자로 받는다. Command는 유스케이스 입력 전체이고 행위 주체도 그 입력의 일부다.
     * 서비스를 1-인자로 유지하면 입력 필드가 늘어도 컨트롤러↔서비스 시그니처가 바뀌지 않는다.
     * (기존 무인자 toCommand()들은 둘 다 미인증 엔드포인트라 주체가 없었을 뿐이다.)
     */
    public SellApplicationCommand toCommand(long sellerId) {
        return new SellApplicationCommand(sellerId, plateNumber);
    }
}
