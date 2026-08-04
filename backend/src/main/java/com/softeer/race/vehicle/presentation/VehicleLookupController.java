package com.softeer.race.vehicle.presentation;

import com.softeer.race.vehicle.application.VehicleLookupService;
import com.softeer.race.vehicle.presentation.request.VehicleLookupRequest;
import com.softeer.race.vehicle.presentation.response.VehicleLookupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles/lookup")
@RequiredArgsConstructor
public class VehicleLookupController implements VehicleLookupApi {

    private final VehicleLookupService vehicleLookupService;

    // @LoginUser 파라미터를 두지 않는다. 비회원이 호출하는 조회라 AuthWebMvcConfig 의 화이트리스트에도
    // 넣지 않았다. 기존 패턴 /api/vehicles/*/images 는 * 가 한 세그먼트라 이 경로를 잡지 않는다
    //
    // 아무것도 만들지 않으므로 201 이 아니라 200 이다
    @Override
    @PostMapping
    public ResponseEntity<VehicleLookupResponse> lookup(@Valid @RequestBody VehicleLookupRequest request) {
        VehicleLookupResponse response =
                VehicleLookupResponse.from(vehicleLookupService.lookup(request.toCommand()));

        return ResponseEntity.ok(response);
    }
}
