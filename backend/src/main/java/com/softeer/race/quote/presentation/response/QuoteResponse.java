package com.softeer.race.quote.presentation.response;

import com.softeer.race.quote.application.dto.info.QuoteInfo;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시세 조회 응답. 차량 제원과 예상 시세를 함께 내려 판매 신청으로 이어지게 한다.
 * <p>
 * 기준가는 넣지 않는다. 예상 시세와 나란히 놓이면 감가율이 역산된다.
 * 소유자명도 넣지 않는다. 호출자가 방금 입력한 값이라 되돌려줄 이유가 없다.
 */
@Schema(description = "시세 조회 응답")
public record QuoteResponse(

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 표시명, 쪼개지 않는다", example = "그랜저 IG")
        String model,

        @Schema(description = "연식", example = "2021")
        int modelYear,

        @Schema(description = "주행거리(km)", example = "45000")
        int mileage,

        @Schema(description = "연료", example = "GASOLINE")
        FuelType fuelType,

        @Schema(description = "대표 이미지 URL, 없는 차량은 null",
                example = "https://cdn.race.dev/vehicles/grandeur-ig.jpg")
        String mainImageUrl,

        @Schema(description = "서버가 산정한 예상 시세(원), 만원 단위로 내림", example = "23200000")
        long estimatedPrice
) {

    public static QuoteResponse from(QuoteInfo info) {
        return new QuoteResponse(
                info.plateNumber(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.mileage(),
                info.fuelType(),
                info.mainImageUrl(),
                info.estimatedPrice());
    }
}
