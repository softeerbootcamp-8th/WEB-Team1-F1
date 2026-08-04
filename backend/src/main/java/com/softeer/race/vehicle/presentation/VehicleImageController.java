package com.softeer.race.vehicle.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.presentation.request.VehicleImageRegisterRequest;
import com.softeer.race.vehicle.presentation.response.VehicleImageRegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/images")
@RequiredArgsConstructor
public class VehicleImageController implements VehicleImageApi {

    private final VehicleImageService vehicleImageService;

    /**
     * 201이 아니라 200이다. 사진 목록을 통째로 바꾸는 교체 연산이라 매번 새 리소스가 생기는 것이
     * 아니고, 가리킬 만한 개별 리소스 주소도 없다.
     * <p>
     * {@code authenticatedUser}를 쓰지 않지만 파라미터로 받는다. 인터셉터가 이 파라미터를 보고
     * 인증을 요구하므로, <b>지우면 인증이 함께 사라진다.</b>
     * <p>
     * TODO 역할 기반 인가가 들어오면 평가사(EVALUATOR)로 좁힌다. 차량 판매자 소유권으로 막지는
     * 않는다 — 사진을 올리는 주체가 판매자가 아니라 방문한 평가사라 정상 흐름이 막힌다.
     */
    @Override
    @PostMapping
    public ResponseEntity<VehicleImageRegisterResponse> register(
            @PathVariable long vehicleId,
            @LoginUser AuthenticatedUser authenticatedUser,
            @Valid @RequestBody VehicleImageRegisterRequest request) {

        return ResponseEntity.ok(VehicleImageRegisterResponse.from(
                vehicleImageService.register(request.toCommand(vehicleId))));
    }
}
