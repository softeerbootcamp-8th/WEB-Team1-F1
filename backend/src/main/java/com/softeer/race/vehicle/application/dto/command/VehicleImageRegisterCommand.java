package com.softeer.race.vehicle.application.dto.command;

import java.util.List;

/**
 * 차량 사진 등록. 이미 업로드가 끝난 주소들을 순서대로 받는다.
 *
 * @param imageUrls 보낸 순서가 그대로 표시 순서가 되고, 첫 번째가 대표 이미지가 된다
 */
public record VehicleImageRegisterCommand(long vehicleId, List<String> imageUrls) {
}
