package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// 요청 하나 단위로 잡히는 자원을 오래 사는 구독이 쥐고 있는지는 실제 요청이 있어야 드러난다
// 서비스를 직접 부르면 요청이 없어 보이지 않으므로 서버를 띄우고 진짜 연결을 연다
//
// 풀을 셋으로 좁힌다. 결함은 보는 사람 수가 커넥션 풀에 묶여 있다는 것이라, 풀을 좁혀 두면
// 그 묶임이 작은 수에서 바로 드러난다. 기본 크기로는 열한 개를 열어야 보이고 그만큼 느리고 흔들린다
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.hikari.maximum-pool-size=3",
                // 막혔을 때 오래 기다리지 않고 바로 드러나게 한다
                "spring.datasource.hikari.connection-timeout=2000"
        })
@DisplayName("구독 연결과 커넥션 풀 통합 테스트")
class RoomStreamPoolIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 풀 크기보다 많이 붙인다. 결함은 벽의 위치가 아니라 보는 사람 수가 풀에 묶여 있다는 것이라,
    // 풀과 같은 수로는 "풀만큼은 된다"까지만 보인다. 넘겨야 묶여 있지 않다는 것이 보인다
    private static final int VIEWERS = 10;

    // 동시 입장에서 겹침을 보는 데 필요한 만큼, 더 늘려도 얻는 것 없이 소켓만 흔들린다
    // 목표 규모인 백 명은 이 값을 올려 한 번 재고 결과를 PR 에 남긴다
    private static final int CROWD = 10;

    // 등록이 영영 안 차면 매달리므로 기다림에 상한을 둔다
    private static final Duration SUBSCRIBE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RELEASE_TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MILLIS = 50L;

    @LocalServerPort
    private int port;

    @Autowired
    private HikariDataSource dataSource;

    @Autowired
    private RoomChannel roomChannel;

    @Autowired
    private SessionService sessionService;

    private final HttpClient client = HttpClient.newHttpClient();
    // 동시 입장 테스트가 여러 스레드에서 더한다
    private final List<StreamRecorder> opened = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> openedRooms = new ArrayList<>();

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    // 열어 둔 연결을 서버가 끝내야 쥐고 있던 것도 함께 풀린다
    // 부모가 테이블을 비우려면 커넥션이 필요하므로, 풀린 것을 확인한 뒤에 넘긴다
    @AfterEach
    void closeStreams() {
        openedRooms.forEach(roomChannel::closeRoom);

        awaitConnectionsReleased();
    }

    @Test
    @DisplayName("시나리오 1 : 풀 크기를 넘겨 구독 -> 열려 있는 동안 DB 커넥션을 쥐지 않는다")
    void openSubscriptionsHoldNoConnection() {
        // given : 진행 중인 방
        long auctionId = liveRoom();

        // when : 풀보다 많은 사람이 실제 연결을 열고 등록까지 끝난다
        openStreams(auctionId);

        // then : 구독은 보낼 것이 없는 동안 아무 커넥션도 쥐고 있지 않다
        // 연결 수를 따라 늘어나면 요청 하나 단위로 쓰고 돌려줄 자원을 수십 분 붙잡는 것이다
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    @DisplayName("시나리오 2 : 풀 크기를 넘겨 구독 -> 경매방과 무관한 기능이 그대로 응답한다")
    void unrelatedRequestStillAnswers() throws Exception {
        // given : 커넥션 풀보다 많은 사람이 경매방을 보고 있다
        long auctionId = liveRoom();
        openStreams(auctionId);

        // when : 경매방과 아무 상관 없는 목록 조회를 부른다
        HttpResponse<Void> response = requestAuctionList();

        // then : 방 하나를 보는 사람 수가 다른 기능의 처리에 영향을 주지 않는다
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    // 이 테스트는 처음부터 초록이다. 스위치와 무관한 성질이라 빨강을 거쳐 만들 수 없고,
    // 나중에 방송 경로를 건드리다 깨뜨리면 그때 잡으라고 두는 그물이다
    @Test
    @DisplayName("시나리오 3 : 여럿이 한꺼번에 입장 -> 연결이 끊기지 않고 나가는 현황이 온전하다")
    void concurrentArrivalsKeepEveryConnection() throws Exception {
        // given : 한 사람이 먼저 붙어 있다, 겹치는 갱신을 이 연결로 받는다
        long auctionId = liveRoom();
        StreamRecorder first = openStream(auctionId);
        awaitSubscribed(auctionId, 1);
        awaitFirstState(first);

        // 들어오기 전에 받아 둔 개수를 기억한다, 이걸 안 하면 첫 현황만으로 통과해 버린다
        int beforeCrowd = first.states().size();

        // when : 나머지가 같은 순간에 들어온다
        openStreamsAtOnce(auctionId, CROWD - 1);
        awaitSubscribed(auctionId, CROWD);

        // then 1 : 겹친 등록에 밀려 걷힌 연결이 없다
        assertThat(roomChannel.viewerCount(auctionId)).isEqualTo(CROWD);

        // then 2 : 먼저 붙어 있던 연결로 갱신이 흘러 들어왔고, 그 현황이 온전하다
        // 겹쳐 쓰다 섞였으면 여기서 조각난 JSON 이 잡힌다
        //
        // 마지막 현황의 접속자 수가 정확히 사람 수라고 보지는 않는다. 브로드캐스트는 유실을 허용하고
        // 정확한 값은 재조회가 주기로 한 설계라, 동시에 들어오면 마지막으로 나간 방송이 한 박자 이전
        // 숫자를 담을 수 있다. 그건 결함이 아니라 정해 둔 성질이므로 여기서 판정하지 않는다
        awaitMoreStates(first, beforeCrowd);
        assertThat(first.states()).hasSizeGreaterThan(beforeCrowd);
        assertThat(first.states().getLast())
                .startsWith("data:")
                .contains("\"auctionId\":" + auctionId)
                .contains("\"phase\":\"LIVE\"")
                .endsWith("}");

        // then 3 : 이만큼 붙어 있는 동안에도 경매방과 무관한 기능이 그대로 응답한다
        // 이 수를 백으로 올려 한 번 재고 그 결과를 PR 에 남긴다
        assertThat(requestAuctionList().statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    // 차례로 열면 겹치는 순간이 없다, 같은 순간에 풀어 줘야 등록과 방송이 서로 밟는다
    private void openStreamsAtOnce(long auctionId, int count) {
        List<String> sessionTokens = new ArrayList<>();
        for (int viewer = 0; viewer < count; viewer++) {
            sessionTokens.add(sessionService.issue(users.user("한구경", Role.DEALER)));
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            CyclicBarrier gate = new CyclicBarrier(count);

            sessionTokens.stream()
                    .map(token -> pool.submit(() -> {
                        await(gate);
                        return openStream(auctionId, token);
                    }))
                    .toList()
                    .forEach(RoomStreamPoolIntegrationTest::join);
        }
    }

    private static void await(CyclicBarrier gate) {
        try {
            gate.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void join(Future<?> opening) {
        try {
            opening.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    // 하나씩 차례로 열면 클라이언트가 뒤 요청을 줄 세워 몇 개를 넘기지 못한다, 한꺼번에 여는 길로 통일한다
    private void openStreams(long auctionId) {
        openStream(auctionId);
        awaitSubscribed(auctionId, 1);

        openStreamsAtOnce(auctionId, VIEWERS - 1);
        awaitSubscribed(auctionId, VIEWERS);
    }

    private StreamRecorder openStream(long auctionId) {
        // 세션 발급도 커넥션을 쓰므로 연결을 열기 전에 끝낸다
        String sessionToken = sessionService.issue(users.user("한구경", Role.DEALER));

        return openStream(auctionId, sessionToken);
    }

    private StreamRecorder openStream(long auctionId, String sessionToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d/api/auctions/%d/room/stream".formatted(port, auctionId)))
                .header(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionToken)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET()
                .build();

        // 받은 줄을 배경에서 모은다, 응답이 끝나지 않으므로 연결은 열린 채로 남는다
        // 이 future 는 본문이 끝나야 완료되므로 기다리지 않는다, 연결이 섰는지는 명부가 차는 것으로 본다
        StreamRecorder recorder = new StreamRecorder();
        client.sendAsync(request, HttpResponse.BodyHandlers.fromLineSubscriber(recorder));

        opened.add(recorder);

        return recorder;
    }

    // 로그인이 필요 없는 공개 API 라, 응답이 늦거나 실패하면 원인은 구독이 쥔 자원뿐이다
    private HttpResponse<Void> requestAuctionList() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d/api/auctions".formatted(port)))
                .timeout(SUBSCRIBE_TIMEOUT)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    // 응답 헤더가 왔다고 서버가 등록까지 끝낸 것은 아니다, 명부에 찰 때까지 기다린 뒤에 잰다
    private void awaitSubscribed(long auctionId, int expected) {
        Instant deadline = Instant.now().plus(SUBSCRIBE_TIMEOUT);

        while (roomChannel.viewerCount(auctionId) < expected) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException(
                        "구독이 " + expected + "개까지 차지 않았다, 연결에서 난 오류 " + streamErrors());
            }

            sleep();
        }
    }

    // 명부가 차는 것과 그 현황이 소켓을 타고 와 줄로 잘리는 것은 다른 시점이다
    // 여기서 던지지 않는다, 못 받으면 뒤따르는 판정이 개수로 드러내는 편이 낫다
    private void awaitFirstState(StreamRecorder recorder) {
        awaitMoreStates(recorder, 0);
    }

    // 들어오기 전 개수보다 늘어날 때까지 기다린다, 첫 현황만 보면 뒤에 온 사람들의 방송을 안 봐도 통과한다
    private void awaitMoreStates(StreamRecorder recorder, int before) {
        Instant deadline = Instant.now().plus(SUBSCRIBE_TIMEOUT);

        while (recorder.states().size() <= before && Instant.now().isBefore(deadline)) {
            sleep();
        }
    }

    // 여기서 던지면 진짜 실패 원인을 정리 단계의 예외가 덮는다, 기다리기만 하고 판정은 테스트에 맡긴다
    private void awaitConnectionsReleased() {
        Instant deadline = Instant.now().plus(RELEASE_TIMEOUT);

        while (dataSource.getHikariPoolMXBean().getActiveConnections() > 0
                && Instant.now().isBefore(deadline)) {
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // 열어 둔 연결 전체에서 난 오류, 등록이 안 찼을 때 원인을 여기서 본다
    private List<String> streamErrors() {
        synchronized (opened) {
            return opened.stream().flatMap(recorder -> recorder.errors.stream()).toList();
        }
    }

    private long liveRoom() {
        long auctionId = rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15)).create();
        openedRooms.add(auctionId);

        return auctionId;
    }

    // 열린 연결로 흘러 들어온 줄을 배경에서 모은다, 본문을 다 읽으려 들면 응답이 끝나지 않아 매달린다
    private static final class StreamRecorder implements Flow.Subscriber<String> {

        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            lines.add(line);
        }

        // 삼키면 등록이 안 찼을 때 이유가 어디에도 안 남는다, 실패 메시지가 스스로 설명하게 모아 둔다
        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable.toString());
        }

        @Override
        public void onComplete() {
        }

        // SSE 는 현황 하나가 data 한 줄이다
        private List<String> states() {
            synchronized (lines) {
                return lines.stream().filter(line -> line.startsWith("data:")).toList();
            }
        }
    }
}
