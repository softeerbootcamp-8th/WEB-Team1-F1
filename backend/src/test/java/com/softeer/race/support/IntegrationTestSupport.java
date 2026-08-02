package com.softeer.race.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.support.seed.AuctionRoomSeeder;
import com.softeer.race.support.seed.UserSeeder;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.VehicleRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// 통합테스트 부모 클래스
// 상속하면 컨텍스트가 개발용 DB 가 아니라 이 클래스가 띄운 MySQL 컨테이너에 붙고,
// 각 테스트가 남긴 행은 다음 테스트로 넘어가지 않는다
// 컨테이너는 이 클래스가 로딩될 때 한 번만 뜨고, 테스트 컨텍스트가 여러 개여도 그 하나를 같이 쓴다
// 종료는 Testcontainers 의 Ryuk 이 스위트가 끝날 때 처리한다
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    // 빈 오버라이드가 컨텍스트 캐시 키에 들어가므로 자식이 각자 선언하면 그 수만큼 컨텍스트가 뜬다
    @TestBean(methodName = "sharedClock", enforceOverride = true)
    private Clock clock;

    static Clock sharedClock() {
        return TestClock.INSTANCE;
    }

    // 개발용 compose 와 같은 MySQL 8.4 LTS, 연결 시간대는 application.yml 과 같은 이유로 넘기지 않는다
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withEnv("TZ", "Asia/Seoul")
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

    // 심는 물건은 저장까지 하므로 리포지토리가 필요하다, 빈으로 등록하는 대신 여기서 만들어 물려준다
    protected UserSeeder users;
    protected AuctionRoomSeeder rooms;

    @Autowired
    void createSeeders(UserRepository userRepository,
                       VehicleRepository vehicleRepository,
                       AuctionPostRepository auctionPostRepository,
                       AuctionRepository auctionRepository,
                       BidRepository bidRepository) {
        users = new UserSeeder(userRepository);
        rooms = new AuctionRoomSeeder(vehicleRepository, auctionPostRepository, auctionRepository, bidRepository);
    }

    // 부모 콜백이 자식보다 먼저 돌아, 앞 테스트가 건 시각을 자식이 자기 시각을 걸기 전에 푼다
    @BeforeEach
    void releaseClock() {
        TestClock.INSTANCE.release();
    }

    protected void fixClockAt(LocalDateTime now) {
        TestClock.INSTANCE.fixAt(now);
    }

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