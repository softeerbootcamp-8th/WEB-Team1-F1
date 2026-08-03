package com.softeer.race.vehicle.presentation.response;

import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "차량 사진 등록 응답")
public record VehicleImageRegisterResponse(

        @Schema(description = "차량 식별자", example = "1")
        long vehicleId,

        @Schema(description = "등록된 이미지 목록. 요청 순서 그대로입니다.")
        List<RegisteredImageResponse> images,

        @Schema(description = "대표 이미지. 보낸 목록의 첫 장입니다. "
                + "이미 만들어진 경매글의 썸네일에는 반영되지 않습니다.",
                example = "https://www.f1race.site/images/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg")
        String thumbnailUrl
) {

    public static VehicleImageRegisterResponse from(VehicleImageRegisterInfo info) {
        return new VehicleImageRegisterResponse(
                info.vehicleId(),
                info.images().stream()
                        .map(image -> new RegisteredImageResponse(image.imageUrl(), image.sortOrder()))
                        .toList(),
                info.thumbnailUrl());
    }

    @Schema(description = "등록된 이미지 한 건")
    public record RegisteredImageResponse(

            @Schema(description = "이미지 주소")
            String imageUrl,

            @Schema(description = "표시 순서. 1부터 시작하며 가장 작은 값이 대표 이미지입니다.", example = "1")
            int sortOrder
    ) {
    }
}
