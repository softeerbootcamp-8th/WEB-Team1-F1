package com.softeer.race.vehicle.presentation;

import com.softeer.race.vehicle.presentation.request.VehicleLookupRequest;
import com.softeer.race.vehicle.presentation.response.VehicleLookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "VehicleLookup", description = "차량 조회 API")
public interface VehicleLookupApi {

    @Operation(summary = "차량 조회",
            description = "번호판과 소유자명으로 차량 제원을 찾아 내려줍니다. 사용자가 \"이 차가 내 차다\"를 "
                    + "확인하는 단계로, 시세 조회와 방문견적 신청의 공통 첫 단계입니다. 로그인이 필요 없습니다. "
                    + "예상 시세와 주행거리는 내려주지 않습니다 — 주행거리는 시점에 따라 변해 원장이 들고 있을 수 "
                    + "없고, 시세는 그 값이 있어야 산정됩니다. 시세가 필요하면 주행거리를 입력받아 "
                    + "POST /api/quotes 를 호출하세요. 기준가도 응답에 넣지 않습니다 — 예상 시세와 나란히 "
                    + "놓이면 감가율이 역산됩니다. "
                    + "미등록 번호판과 소유자명 불일치는 같은 404로 응답합니다 — 구분해서 알려주면 번호판을 "
                    + "바꿔 넣어보며 소유자명을 알아낼 수 있습니다. "
                    + "조회인데 POST 인 이유는 번호판과 실명을 쿼리 파라미터로 보내면 액세스 로그와 브라우저 "
                    + "히스토리에 그대로 남기 때문입니다.")
    ResponseEntity<VehicleLookupResponse> lookup(VehicleLookupRequest request);
}
