package com.softeer.race.auctionpost.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.vehicle.domain.Vehicle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    private String thumbnailUrl;

    private LocalDateTime publishedAt;

    private LocalDateTime deletedAt;

    /**
     * 경매글을 만든다. 발행 시각은 Auction.schedule의 최소 리드타임 계산 기준이 된다.
     */
    public static AuctionPost create(Vehicle vehicle, String thumbnailUrl, LocalDateTime now) {
        AuctionPost post = new AuctionPost();
        post.vehicle = vehicle;
        post.thumbnailUrl = thumbnailUrl;
        post.publishedAt = now;

        return post;
    }

    public void delete(LocalDateTime now) {
        if (deletedAt != null) {
            return;
        }

        this.deletedAt = now;
    }
}
