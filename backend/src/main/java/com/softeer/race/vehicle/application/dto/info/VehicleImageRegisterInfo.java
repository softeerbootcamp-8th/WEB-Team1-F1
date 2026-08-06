package com.softeer.race.vehicle.application.dto.info;

import com.softeer.race.vehicle.domain.VehicleImage;

import java.util.List;

/**
 * 등록 결과
 */
public record VehicleImageRegisterInfo(
        long vehicleId,
        List<RegisteredImage> images
) {

    public static VehicleImageRegisterInfo from(long vehicleId, List<VehicleImage> images) {
        List<RegisteredImage> registered = images.stream()
                .map(image -> new RegisteredImage(image.getImageUrl(), image.getSortOrder()))
                .toList();

        return new VehicleImageRegisterInfo(vehicleId, registered);
    }

    public record RegisteredImage(String imageUrl, int sortOrder) {
    }
}
