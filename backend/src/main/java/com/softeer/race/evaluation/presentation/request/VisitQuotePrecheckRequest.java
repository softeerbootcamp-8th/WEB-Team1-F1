package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.vehicle.application.dto.command.VehicleLookupCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 방문견적 예약 화면에 들어가기 전에 차량과 진행 중인 신청을 확인하는 요청. */
@Schema(description = "방문견적 사전 확인 요청")
public record VisitQuotePrecheckRequest(

        @Schema(description = "차량 번호판(공백·대시 없이)", example = "12가3456")
        @NotBlank
        @Pattern(regexp = "^\\d{2,3}[가-힣]\\d{4}$",
                message = "번호판은 공백과 대시 없이 12가3456 형식이어야 합니다.")
        String plateNumber,

        @Schema(description = "차량 소유자명", example = "김민수")
        @NotBlank
        @Size(max = 50, message = "소유자명은 50자 이하여야 합니다.")
        String ownerName
) {

    public VehicleLookupCommand toCommand() {
        return new VehicleLookupCommand(plateNumber, ownerName.trim());
    }
}
