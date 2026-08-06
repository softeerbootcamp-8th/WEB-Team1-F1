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
        AuctionPost post = AuctionPost.create(null, NOW);

        assertThat(post.getPostStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제하면 삭제 시각이 채워진다.")
    void delete_삭제시각_채움() {
        AuctionPost post = AuctionPost.create(null, NOW);

        post.delete(NOW.plusDays(1));

        assertThat(post.getDeletedAt()).isEqualTo(NOW.plusDays(1));
    }

    // 재시도로 delete가 두 번 불려도 최초 삭제 시각을 그대로 유지해야 한다
    @Test
    @DisplayName("이미 삭제된 경매글을 다시 삭제해도 최초 삭제 시각이 유지된다.")
    void delete_재시도해도_최초_삭제시각_유지() {
        AuctionPost post = AuctionPost.create(null, NOW);
        post.delete(NOW.plusDays(1));

        post.delete(NOW.plusDays(2));

        assertThat(post.getDeletedAt()).isEqualTo(NOW.plusDays(1));
    }
}
