package com.softeer.race.vehicle.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int modelYear;

    @Column(nullable = false)
    private int mileage;

    @Column(nullable = false)
    private String fuelType;

    @Column(nullable = false)
    private String transmission;

    @Column(nullable = false)
    private String plateNumber;

    private Long estimatedPrice;
}
