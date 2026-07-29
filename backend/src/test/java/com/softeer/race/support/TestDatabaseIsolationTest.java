package com.softeer.race.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

// 통합테스트가 개발용 DB 에 붙지 않는다는 것이 이 작업의 계약이라 그 지점을 고정한다
@DisplayName("테스트 DB 격리")
class TestDatabaseIsolationTest extends IntegrationTestSupport {

    private static final String DEV_SCHEMA = "race";
    private static final String DEV_DATASOURCE = "localhost:3306/race";

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("통합테스트는 개발용 DB 가 아닌 컨테이너에 붙는다")
    void connectsToContainerNotDevDatabase() throws SQLException {
        String schema = jdbcTemplate.queryForObject("select database()", String.class);
        assertThat(schema).isNotEqualTo(DEV_SCHEMA);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).doesNotContain(DEV_DATASOURCE);
        }
    }
}