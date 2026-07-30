package com.softeer.race.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

// 통합테스트 부모 클래스
// 상속하면 컨텍스트가 개발용 DB 가 아니라 이 클래스가 띄운 MySQL 컨테이너에 붙고,
// 각 테스트가 남긴 행은 다음 테스트로 넘어가지 않는다
// 컨테이너는 이 클래스가 로딩될 때 한 번만 뜨고, 테스트 컨텍스트가 여러 개여도 그 하나를 같이 쓴다
// 종료는 Testcontainers 의 Ryuk 이 스위트가 끝날 때 처리한다
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    // 개발용 compose 와 같은 MySQL 8.4 LTS, 시간대도 같게 맞춰 저장 시각 검증이 흔들리지 않게 한다
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withEnv("TZ", "Asia/Seoul")
            .withUrlParam("serverTimezone", "Asia/Seoul")
            .withUrlParam("characterEncoding", "UTF-8");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MockMvc mockMvc;

    // 테스트 전이 아니라 후에 지운다
    // @Sql 픽스처는 스프링 리스너가 JUnit 콜백보다 먼저 실행하므로, 앞에서 지우면 픽스처가 날아간다
    @AfterEach
    void clearTables() {
        jdbcTemplate.execute("set foreign_key_checks = 0");
        tableNames().forEach(table -> jdbcTemplate.execute("delete from `" + table + "`"));
        jdbcTemplate.execute("set foreign_key_checks = 1");
    }

    // FK 순서를 손으로 적으면 테이블이 늘 때마다 같이 고쳐야 하므로 스키마에서 읽는다
    private List<String> tableNames() {
        return jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = database()",
                String.class);
    }
}