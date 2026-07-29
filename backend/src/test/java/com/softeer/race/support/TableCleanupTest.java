package com.softeer.race.support;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

// 정리 훅은 테이블 목록을 못 찾아도 조용히 통과하므로, 실제로 지워지는지를 순서로 고정한다
// 앞 테스트가 남긴 행이 뒤 테스트에 보이면 깨진다
@DisplayName("테스트 간 데이터 격리")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TableCleanupTest extends IntegrationTestSupport {

    private static final long LEFTOVER_USER_ID = 777L;

    @Test
    @Order(1)
    @DisplayName("앞 테스트가 행을 남긴다")
    void leavesRow() {
        jdbcTemplate.update("""
                insert into users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
                values (?, 'leftover', 'leftover@race.dev', 'pw', '남은사람', '01000000777', '서울', 'GENERAL', now(), now())
                """, LEFTOVER_USER_ID);

        assertThat(countUsers()).isOne();
    }

    @Test
    @Order(2)
    @DisplayName("뒤 테스트는 빈 테이블에서 시작한다")
    void startsClean() {
        assertThat(countUsers()).isZero();
    }

    private Integer countUsers() {
        return jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
    }
}