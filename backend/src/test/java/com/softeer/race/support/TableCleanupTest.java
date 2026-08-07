package com.softeer.race.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 정리 훅은 테이블 목록을 못 찾아도 조용히 통과하므로, 실제로 지워지는지를 여기서 고정한다
// 두 테스트가 같은 일을 한다, 어느 쪽이 먼저 돌든 나중에 도는 쪽이 앞이 남긴 행을 보면 깨진다
@DisplayName("테스트 간 데이터 격리")
class TableCleanupTest extends IntegrationTestSupport {

    @Test
    @DisplayName("빈 테이블에서 시작해 행을 하나 남긴다")
    void startsCleanAndLeavesRow() {
        startCleanThenInsert(771L, "leftover1");
    }

    @Test
    @DisplayName("형제 테스트가 남긴 행이 보이지 않는다")
    void doesNotSeeRowFromSibling() {
        startCleanThenInsert(772L, "leftover2");
    }

    // 식별자를 다르게 둔다, 정리가 안 됐을 때 중복 키가 아니라 개수 단언으로 드러나야 원인이 보인다
    private void startCleanThenInsert(long id, String username) {
        assertThat(countUsers()).isZero();

        jdbcTemplate.update("""
                insert into users (id, username, email, password, real_name, phone, role, created_at, updated_at)
                values (?, ?, ?, 'pw', '남은사람', '01000000777', 'GENERAL', now(), now())
                """, id, username, username + "@race.dev");

        assertThat(countUsers()).isOne();
    }

    private Integer countUsers() {
        return jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
    }
}