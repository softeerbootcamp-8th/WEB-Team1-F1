package com.softeer.race.vehicle.presentation.request;

import com.softeer.race.vehicle.application.dto.command.VehicleLookupCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 차량 조회 요청.
 * <p>
 * 검증 규칙을 {@code QuoteRequest}와 같게 둔다. 두 API가 같은 원장의 같은 컬럼을 대조하므로 규칙이
 * 갈라지면 한쪽만 통과하는 값이 생겨, 조회는 되는데 시세는 400 이 되는 상태가 만들어진다.
 * <p>
 * 번호판 정규화를 조회 구현체에 넣지 않는 대신 여기서 형식을 강제한다. 구현체마다 정규화 규칙을
 * 다시 구현하면 두 규칙이 어긋나는 순간 조회 키가 갈라진다.
 * <p>
 * 소유자명은 형식을 강제하지 않는다. 외국인 이름이나 성 없는 이름을 정규식으로 막을 근거가 없어서
 * 길이 상한만 두고 앞뒤 공백만 다듬는다.
 */
@Schema(description = "차량 조회 요청")
public record VehicleLookupRequest(

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

    /** 인자가 없다. 비인증 엔드포인트라 Command 에 담을 행위 주체가 없다 */
    public VehicleLookupCommand toCommand() {
        return new VehicleLookupCommand(plateNumber, ownerName.trim());
    }
}
