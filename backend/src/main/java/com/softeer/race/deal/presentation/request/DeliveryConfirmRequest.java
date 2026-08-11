package com.softeer.race.deal.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 구매자가 잡는 인도 일정
 * <p>
 * 동의 여부를 따로 받지 않는다. 이 요청을 보내는 것이 곧 탁송 일정에 동의한다는 뜻이다.
 * 불리언을 받으면 false 로 보낼 수 있는 통로가 생기는데, 그 경우에 서버가 할 일이 없다
 */
@Schema(description = "인도 일정 확정")
public record DeliveryConfirmRequest(

        @Schema(description = "차량을 받는 일시, 탁송 일시보다 뒤여야 한다",
                example = "2026-08-21T10:00:00")
        @NotNull(message = "인도 일시는 필수입니다.")
        LocalDateTime deliveryAt,

        @Schema(description = "인도 장소", example = "부산시 해운대구 센텀중앙로 55")
        @NotBlank(message = "인도 장소는 필수입니다.")
        String deliveryLocation
) {
}
