package com.softeer.race.vehicle.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.vehicle.presentation.request.VehicleImageRegisterRequest;
import com.softeer.race.vehicle.presentation.response.VehicleImageRegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "VehicleImage", description = "차량 사진 API")
public interface VehicleImageApi {

    @Operation(summary = "차량 사진 등록",
            description = """
                    업로드를 마친 이미지 주소들을 차량에 등록합니다. 업로드 주소 발급 API가 돌려준
                    fileUrl 을 그대로 보내야 하며, 그 외의 주소는 거부됩니다.

                    이 요청은 차량의 사진 목록을 통째로 교체합니다. 판매 신청 시 자동으로 넣어 둔
                    카탈로그 이미지는 이 시점에 삭제되고, 보낸 첫 번째 사진이 대표 이미지가 됩니다.
                    경매글이 있으면 경매 목록·경매방에 보이는 썸네일도 함께 갱신됩니다.

                    세션 쿠키가 필요합니다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록된 목록과 대표 이미지를 반환합니다.")
    @ApiResponse(responseCode = "400",
            description = "이미지가 없거나 20장을 넘는 경우, 또는 이 서비스가 발급하지 않은 주소가 섞인 경우입니다. "
                    + "한 건이라도 잘못되면 아무것도 저장하지 않습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 차량입니다.")
    ResponseEntity<VehicleImageRegisterResponse> register(
            @Parameter(description = "차량 식별자", example = "1") long vehicleId,
            AuthenticatedUser authenticatedUser,
            VehicleImageRegisterRequest request);
}
