package com.softeer.race.vehicle.presentation;

import com.softeer.race.vehicle.presentation.response.DemoVehicleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "DemoVehicle", description = "데모 차량 안내 API")
public interface DemoVehicleApi {

    @Operation(summary = "데모 차량 안내",
            description = "시세 조회와 내 차 팔기에 넣어 볼 수 있는 데모 차량을 최대 10대 내려줍니다. "
                    + "로그인이 필요 없습니다 — 두 화면 모두 비로그인으로 쓸 수 있어, 안내만 로그인을 "
                    + "요구하면 안내가 필요한 사람이 안내를 볼 수 없게 됩니다. "
                    + "판매 신청이나 경매가 진행 중이어서 다시 쓸 수 없는 차량은 빠집니다 — 판정 기준이 "
                    + "방문견적 중복 검사와 같아, 여기 있는 값은 그대로 넣어도 중복으로 거절되지 않습니다. "
                    + "다만 조회 직후 남이 먼저 신청하는 시간차까지는 막지 않습니다. "
                    + "실제 회원이나 실제 출품 차량은 담기지 않습니다 — 데모용 가상 차량 원장만 읽습니다. "
                    + "쓸 수 있는 차량이 없으면 빈 배열로 200 응답합니다.")
    ResponseEntity<List<DemoVehicleResponse>> list();
}