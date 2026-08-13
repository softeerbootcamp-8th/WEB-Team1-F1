package com.softeer.race.support;

import com.softeer.race.auction.application.AuctionCloser;
import com.softeer.race.auction.application.AuctionStarter;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auth.config.AuthProperties;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.auth.domain.SessionTokenGenerator;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.support.seed.AuctionRoomSeeder;
import com.softeer.race.support.seed.SessionSeeder;
import com.softeer.race.support.seed.UserSeeder;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleKeywordTagRepository;
import com.softeer.race.vehicle.domain.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
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

    // 개발용 compose 와 같은 태그, 그쪽과 같이 비밀번호를 걸지 않는다
    private static final int REDIS_PORT = 6379;
    private static final GenericContainer REDIS = new GenericContainer(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(REDIS_PORT);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    // 이걸 걸지 않으면 auto-config 의 기본값인 개발자 노트북의 localhost:6379 로 붙는다
    // 노출 포트는 호스트에서 임의 포트로 매핑되므로 6379 를 그대로 쓰지 않는다
    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    protected MockMvc mockMvc;

    // 심는 물건은 저장까지 하므로 리포지토리가 필요하다, 빈으로 등록하는 대신 여기서 만들어 물려준다
    protected UserSeeder users;
    protected AuctionRoomSeeder rooms;
    // 세션은 테이블이 아니라 Redis 에 살아 SQL 픽스처로 심을 수 없다, 이 시더가 그 자리를 대신한다
    protected SessionSeeder sessions;

    @Autowired
    void createSeeders(UserRepository userRepository,
                       VehicleRepository vehicleRepository,
                       VehicleImageRepository vehicleImageRepository,
                       VehicleKeywordTagRepository vehicleKeywordTagRepository,
                       AuctionPostRepository auctionPostRepository,
                       AuctionRepository auctionRepository,
                       BidRepository bidRepository,
                       AuctionStarter auctionStarter,
                       AuctionCloser auctionCloser,
                       SessionStore sessionStore,
                       SessionTokenGenerator sessionTokenGenerator,
                       AuthProperties authProperties) {
        users = new UserSeeder(userRepository);
        sessions = new SessionSeeder(sessionStore, sessionTokenGenerator, authProperties.session().ttl());
        rooms = new AuctionRoomSeeder(vehicleRepository, vehicleImageRepository, vehicleKeywordTagRepository,
                auctionPostRepository, auctionRepository, bidRepository, auctionStarter, auctionCloser);
    }

    // 부모 콜백이 자식보다 먼저 돌아, 앞 테스트가 건 시각을 자식이 자기 시각을 걸기 전에 푼다
    @BeforeEach
    void releaseClock() {
        TestClock.INSTANCE.release();
    }

    protected void fixClockAt(LocalDateTime now) {
        TestClock.INSTANCE.fixAt(now);
    }

    // 컨테이너가 스위트 전체에서 하나라 지우지 않으면 앞 테스트가 넣은 키가 다음 테스트에 남는다
    // 테이블과 달리 스키마가 없어 지울 대상을 물어볼 곳이 없다, 통째로 비운다
    @AfterEach
    void clearRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
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