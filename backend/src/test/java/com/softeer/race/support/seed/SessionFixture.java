package com.softeer.race.support.seed;

import static com.softeer.race.user.domain.Role.DEALER;
import static com.softeer.race.user.domain.Role.EVALUATOR;
import static com.softeer.race.user.domain.Role.GENERAL;

import java.time.Duration;

/**
 * SQL 픽스처가 심는 회원에 대응하는 로그인 세션
 * <p>
 * 세션만 Redis 에 살아 {@code @Sql} 로 함께 심을 수 없다. 대신 파일 하나에 픽스처별로 모아 두어,
 * 토큰과 회원의 대응이 SQL 때처럼 한 곳에서만 관리되게 한다. 짝이 되는 SQL 파일과 이름을 맞춘다.
 * <p>
 * <b>만료된 세션은 심지 않는다.</b> Redis 는 만료된 키를 스스로 지우므로 심지 않은 상태가 곧
 * 만료된 상태이고, 만료를 확인하는 시나리오는 없는 토큰을 그대로 보내면 된다.
 */
public final class SessionFixture {

    private SessionFixture() {
    }

    /** auth-session-fixture.sql */
    public static void authSession(SessionSeeder sessions) {
        // 갱신 임계(15분)를 사이에 두고 갈리는 두 세션이다, 슬라이딩 갱신 시나리오가 이 차이를 본다
        sessions.seed("renewable-raw-token", 81, GENERAL, Duration.ofMinutes(10));
        sessions.seed("fresh-raw-token", 81, GENERAL, Duration.ofMinutes(25));
    }

    /** bid-place-fixture.sql */
    public static void bidPlace(SessionSeeder sessions) {
        sessions.seed("token-seller", 51, GENERAL);
        sessions.seed("token-alice", 52, DEALER);
        sessions.seed("token-bob", 53, DEALER);
        sessions.seed("token-evaluator", 54, EVALUATOR);
    }

    /** notification-fixture.sql — 타인 계정으로 로그인해 볼 시나리오가 없어 72는 심지 않는다 */
    public static void notification(SessionSeeder sessions) {
        sessions.seed("notification-my-token", 71, GENERAL);
    }

    /** visit-quote-fixture.sql */
    public static void visitQuote(SessionSeeder sessions) {
        sessions.seed("visit-quote-raw-token", 400, GENERAL);
        sessions.seed("visit-quote-other-raw-token", 401, GENERAL);
        sessions.seed("visit-quote-eval-raw-token", 402, EVALUATOR);
    }

    /** diagnostic-report-fixture.sql */
    public static void diagnosticReport(SessionSeeder sessions) {
        sessions.seed("report-seller-token", 600, GENERAL);
        sessions.seed("report-eval-token", 601, EVALUATOR);
        sessions.seed("report-eval2-token", 602, EVALUATOR);
        sessions.seed("report-other-token", 603, GENERAL);
        sessions.seed("report-seller2-token", 604, GENERAL);
    }

    /** evaluation-assignment-fixture.sql */
    public static void evaluationAssignment(SessionSeeder sessions) {
        sessions.seed("assign-kim-raw-token", 500, EVALUATOR);
        sessions.seed("assign-lee-raw-token", 501, EVALUATOR);
        sessions.seed("assign-park-raw-token", 502, GENERAL);
    }

    /** evaluation-result-patch-fixture.sql */
    public static void evaluationResultPatch(SessionSeeder sessions) {
        sessions.seed("patch-seller-token", 700, GENERAL);
        sessions.seed("patch-eval-token", 701, EVALUATOR);
        sessions.seed("patch-eval2-token", 702, EVALUATOR);
    }
}
