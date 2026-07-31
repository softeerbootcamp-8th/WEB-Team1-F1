package com.softeer.race.vehicle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {
    Optional<VehicleImage> findFirstByVehicleOrderBySortOrderAsc(Vehicle vehicle);
}
