package com.softeer.race.vehicle.application.dto.info;

import com.softeer.race.vehicle.domain.VehicleImage;

import java.util.List;

/**
 * 등록 결과.
 *
 * @param thumbnailUrl 대표 이미지. 경매글이 있으면 그 썸네일도 이 값으로 갱신된 상태다
 */
public record VehicleImageRegisterInfo(
        long vehicleId,
        List<RegisteredImage> images,
        String thumbnailUrl
) {

    /**
     * {@code images}는 비어 있을 수 없다. 요청 검증이 최소 한 건을 강제하므로 서비스가 빈 목록으로
     * 여기까지 오지 않는다.
     */
    public static VehicleImageRegisterInfo from(long vehicleId, List<VehicleImage> images) {
        List<RegisteredImage> registered = images.stream()
                .map(image -> new RegisteredImage(image.getImageUrl(), image.getSortOrder()))
                .toList();

        return new VehicleImageRegisterInfo(vehicleId, registered, registered.getFirst().imageUrl());
    }

    public record RegisteredImage(String imageUrl, int sortOrder) {
    }
}
