package com.softeer.race.vehicle.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum VehicleErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "차량을 찾을 수 없습니다."),

    /**
     * 클라이언트가 우리가 발급하지 않은 주소를 보냈다. 이 검사가 없으면 로그인한 사용자가
     * 임의의 외부 주소를 차량 이미지로 박아 넣을 수 있다.
     */
    UNMANAGED_IMAGE_URL(HttpStatus.BAD_REQUEST,
            "이 서비스에서 발급한 이미지 주소가 아닙니다. 업로드 주소 발급 API가 돌려준 값을 그대로 보내야 합니다.");

    private final HttpStatus status;
    private final String message;

    VehicleErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙여 {@code NOT_FOUND}가 {@code VEHICLE_NOT_FOUND}로 나간다.
     * {@code AuctionErrorCode.VEHICLE_NOT_FOUND}와 <b>같은 문자열</b>이 되는데, 의도한 것이다.
     * 둘 다 "그 id의 차량 행이 없다"는 같은 사실을 말하므로 프론트가 구별할 이유가 없다.
     * ({@code SellErrorCode.VEHICLE_NOT_FOUND}는 "카탈로그에 없는 번호판"이라 사실이 다르고,
     * 그래서 그쪽만 {@code SELL_} 접두사로 갈라져 있다.)
     */
    @Override
    public String code() {
        return "VEHICLE_" + name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
