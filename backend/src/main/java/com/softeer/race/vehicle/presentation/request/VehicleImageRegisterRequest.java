package com.softeer.race.vehicle.presentation.request;

import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 차량 사진 등록 요청. 업로드 주소 발급 API가 돌려준 {@code fileUrl}들을 표시할 순서대로 보낸다.
 * <p>
 * 이 요청은 차량의 사진 목록을 <b>통째로 교체</b>한다. 한 장을 추가하려면 기존 목록에 그 한 장을
 * 더해 전부 보내야 한다. 부분 추가·삭제 API를 따로 두지 않는 이유는 순서가 곧 데이터이고,
 * 부분 연산으로는 순서를 다시 매기는 규칙을 서버와 클라이언트가 두 벌로 갖게 되기 때문이다.
 */
@Schema(description = "차량 사진 등록 요청")
public record VehicleImageRegisterRequest(

        @Schema(description = "업로드를 마친 이미지 주소 목록. 보낸 순서가 표시 순서이며 첫 번째가 대표 이미지가 됩니다.",
                example = "[\"https://www.f1race.site/images/evaluations/2026/08/3f2b1c8e.jpg\"]")
        @NotEmpty(message = "등록할 이미지가 최소 한 장은 필요합니다.")
        @Size(max = VehicleImageRegisterRequest.MAX_IMAGE_COUNT,
                message = "이미지는 " + VehicleImageRegisterRequest.MAX_IMAGE_COUNT + "장까지 등록할 수 있습니다.")
        List<@NotBlank(message = "이미지 주소는 비어 있을 수 없습니다.") String> imageUrls
) {

    static final int MAX_IMAGE_COUNT = 20;

    public VehicleImageRegisterCommand toCommand(long vehicleId) {
        return new VehicleImageRegisterCommand(vehicleId, imageUrls);
    }
}
