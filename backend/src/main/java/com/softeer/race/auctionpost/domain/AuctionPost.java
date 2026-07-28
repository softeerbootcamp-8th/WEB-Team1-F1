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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false, unique = true)
    private Vehicle vehicle;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus postStatus;

    private LocalDateTime publishedAt;

    private LocalDateTime deletedAt;

    /**
     * 임시저장 없이 곧바로 발행 상태로 경매글을 만든다
     */
    public static AuctionPost create(Vehicle vehicle, String title, String description, String thumbnailUrl, LocalDateTime now) {
        AuctionPost post = new AuctionPost();
        post.vehicle = vehicle;
        post.title = title;
        post.description = description;
        post.thumbnailUrl = thumbnailUrl;
        post.postStatus = PostStatus.PUBLISHED;
        post.publishedAt = now;

        return post;
    }
}
