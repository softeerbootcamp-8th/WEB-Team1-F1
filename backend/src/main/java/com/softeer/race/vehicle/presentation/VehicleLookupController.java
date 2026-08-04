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

    // @LoginUser 파라미터를 두지 않는다. 비회원이 호출하는 조회이고, AuthInterceptor 가 핸들러
    // 파라미터에 @LoginUser 가 있는지로 인증 요구를 판정하므로 선언하지 않으면 공개로 통과한다.
    // 여기에 @LoginUser 를 붙이는 순간 비회원 시세 조회의 앞단이 401 이 된다
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
