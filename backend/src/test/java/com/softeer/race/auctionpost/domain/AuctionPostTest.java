package com.softeer.race.auctionpost.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionPostTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 27, 15, 0);

    @Test
    @DisplayName("경매글은 발행 상태로 생성된다.")
    void create_발행상태() {
        AuctionPost post = AuctionPost.create(null, "2021 그랜저 IG", "무사고", "https://cdn/1.jpg", NOW);

        assertThat(post.getPostStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(NOW);
        assertThat(post.getTitle()).isEqualTo("2021 그랜저 IG");
        assertThat(post.getThumbnailUrl()).isEqualTo("https://cdn/1.jpg");
    }
}
