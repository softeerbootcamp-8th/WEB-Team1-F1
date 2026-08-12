package com.softeer.race.vehicle.presentation;

import com.softeer.race.vehicle.application.DemoVehicleService;
import com.softeer.race.vehicle.presentation.response.DemoVehicleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles/demo")
@RequiredArgsConstructor
public class DemoVehicleController implements DemoVehicleApi {

    private final DemoVehicleService demoVehicleService;

    // @LoginUser 파라미터를 두지 않는다. AuthInterceptor 가 그것으로 인증 요구를 판정하므로
    // 선언하지 않으면 공개로 통과한다. 붙이는 순간 비회원 안내가 401 이 된다
    //
    // 조회 조건이 없어 GET 이다. 차량 조회가 POST 인 이유(번호판·실명이 URL 에 남는다)가 여기엔 없다
    @Override
    @GetMapping
    public ResponseEntity<List<DemoVehicleResponse>> list() {
        return ResponseEntity.ok(DemoVehicleResponse.from(demoVehicleService.list()));
    }
}