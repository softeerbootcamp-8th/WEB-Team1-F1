package com.softeer.race.auctionroom.experiment;

import com.softeer.race.bid.application.BidIncrementService;
import com.softeer.race.bid.domain.BidIncrementTable;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.jdbc.Sql;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 실험이지 테스트가 아니다. 레이스를 확률로 잡으므로 초록이 아무것도 보장하지 않는다.
// 기본 test 태스크에서 제외하고 gradle experiment 로 따로 돌린다. 결과 수치는 이슈 #170 에 남긴다.
//
// 격리하지 않는 것이 요점이다. 커밋 경계와 실제 조회 지연과 실제 소켓 쓰기가 겹쳐야 재현되는 현상이라,
// 셋 중 하나라도 목으로 바꾸면 창이 닫힌다.
//
// 조건은 시스템 프로퍼티로 빼고 하네스 동작에 속하는 값은 상수로 둔다.
//   ./gradlew experiment -Psubscribers=20 -Pslow=3 -Pruns=3
@Tag("experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "race.scheduling.enabled=true")
@Sql("/sql/bid-increment-bands.sql")
// 이름이 Test 로 끝나지 않는 것이 의도다, 초록이 아무것도 보장하지 않아 테스트로 읽히면 안 된다
@SuppressWarnings("JUnitTestClassNamingConvention")
class BroadcastOrderExperiment extends IntegrationTestSupport {

    private static final int SUBSCRIBERS = Integer.getInteger("subscribers", 10);
    private static final int SLOW_SUBSCRIBERS = Integer.getInteger("slow", 0);

    // 느린 구독이 초당 읽는 양, 0 이면 한 바이트도 안 읽는다
    private static final int SLOW_READ_KBPS = Integer.getInteger("slowKbps", 0);
    private static final int BIDDERS = Integer.getInteger("bidders", 5);

    // 입찰이 도는 중에 뒤늦게 붙는 구독 수다, 연결 만료로 전원이 한꺼번에 돌아오는 순간을 흉내 낸다
    private static final int LATE_SUBSCRIBERS = Integer.getInteger("late", 0);

    // 입찰 시작 뒤 이만큼 지나서 붙인다
    private static final Duration LATE_AFTER = Duration.ofSeconds(Integer.getInteger("lateAfter", 30));
    private static final int RUNS = Integer.getInteger("runs", 1);
    private static final Duration BIDDING = Duration.ofSeconds(Integer.getInteger("seconds", 20));

    private static final Duration BID_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(10);
    private static final Duration DRAIN = Duration.ofSeconds(3);
    private static final long START_PRICE = 10_000_000L;

    // 작게 잡을수록 빨리 찬다, 안 읽는 구독자의 수신 버퍼가 차야 서버 쓰기가 막힌다
    private static final int SLOW_RECEIVE_BUFFER_BYTES = 4096;

    // 이 간격마다 한 번씩 정해진 양을 읽는다, 짧을수록 흐름이 고르지만 스레드 깨우기가 잦다
    private static final long SLOW_READ_TICK_MILLIS = 100L;

    private static final Pattern CURRENT_PRICE = Pattern.compile("\"currentPrice\"\\s*:\\s*(\\d+)");

    @LocalServerPort
    private int port;

    @Autowired
    private BidIncrementService bidIncrementService;

    // 조건이 실제로 서버에 닿았는지 확인하는 용도다, 프로퍼티 이름이 틀리면 조용히 기본값으로 돌아간다
    @Autowired
    private Environment environment;

    @Test
    @DisplayName("동시 입찰 중 구독자가 받은 현재가 수열에 역전이 있는지 센다")
    void countPriceInversions() throws Exception {
        System.out.printf("조건 구독 %d 뒤늦게 %d개 %d초뒤 느린구독 %d 읽기속도 %dKB/s"
                        + " 입찰자 %d 회차 %d 구간 %d초 일꾼 %s 커넥션풀 %s%n",
                SUBSCRIBERS, LATE_SUBSCRIBERS, LATE_AFTER.toSeconds(),
                SLOW_SUBSCRIBERS, SLOW_READ_KBPS, BIDDERS, RUNS, BIDDING.toSeconds(),
                environment.getProperty("race.room.broadcast-workers", "설정없음"),
                environment.getProperty("spring.datasource.hikari.maximum-pool-size", "설정없음"));

        long totalInversions = 0;

        for (int run = 1; run <= RUNS; run++) {
            // 앞 회차의 연결이 서버에 남은 채 다음 회차가 시작하면 표본이 오염된다
            // 회차마다 경매를 새로 만들어 방이 갈리므로 이 쉼은 정리를 기다리는 것이 아니라
            // 앞 회차의 소켓과 스레드가 실제로 접히는 틈이다
            if (run > 1) {
                Thread.sleep(COOLDOWN.toMillis());
            }

            totalInversions += runOnce(run);
        }

        System.out.printf("전체 %d 회차 역전 합계 %d%n", RUNS, totalInversions);
    }

    private long runOnce(int run) throws Exception {
        // 상태가 아니라 시각으로 단계를 판정하므로 시작 시각만 과거면 진행 중이다
        LocalDateTime startAt = LocalDateTime.now().minusMinutes(1);
        User seller = users.user("판매자", Role.GENERAL);
        long auctionId = rooms.room(seller, startAt).startPrice(START_PRICE).create();

        ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
        HttpClient http = HttpClient.newBuilder().executor(threads).build();
        List<StalledReader> stalled = new ArrayList<>();

        // 계정과 세션을 전부 먼저 만든다. 시더가 전역 TestClock 을 과거로 고정했다 푸는데,
        // 연결이 열린 뒤에 부르면 그 사이 서버가 과거 시각으로 단계를 판정해 구독을 거절한다
        List<String> watchers = logins("구독자", SUBSCRIBERS);
        List<String> stallers = logins("안읽는구독자", SLOW_SUBSCRIBERS);
        List<String> bidders = logins("입찰자", BIDDERS);

        try {
            int early = SUBSCRIBERS - LATE_SUBSCRIBERS;
            Audience audience = openSubscriptions(auctionId, watchers.subList(0, early), threads, http);
            stalled.addAll(openStalledSubscriptions(auctionId, stallers, threads));

            // 입찰을 옆으로 돌려 두고 그 사이에 뒤 무리를 붙인다, 붙는 순간이 입찰과 겹쳐야 재는 뜻이 있다
            Future<BidOutcome> bidding = threads.submit(() -> runBidders(auctionId, bidders, threads, http));

            Surge surge = joinLate(auctionId, watchers.subList(early, SUBSCRIBERS), audience, threads, http);
            BidOutcome outcome = bidding.get();

            // 닫기 전에 읽은 양을 먼저 챙긴다, 속도 조절기가 먹었는지 보는 자기 검증이다
            long slowBytes = stalled.stream().mapToLong(StalledReader::bytesRead).sum();

            // 막힌 소켓을 먼저 닫아 서버 쓰기를 풀어준다
            // 안 그러면 거기 걸려 있던 입찰이 테이블 정리 뒤에 깨어나 없는 회원을 참조한다
            stalled.forEach(StalledReader::close);
            stalled.clear();

            // 마지막 방송이 도착할 틈을 준다, 곧바로 끊으면 보낸 것과 받은 것이 어긋난다
            Thread.sleep(DRAIN.toMillis());

            http.shutdownNow();
            threads.shutdownNow();

            if (!threads.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("경고: 구독 읽기 스레드가 시간 안에 끝나지 않았다, 뒤쪽 표본이 빠졌을 수 있다");
            }

            return report(run, audience, outcome, slowBytes, surge);
        } finally {
            stalled.forEach(StalledReader::close);
        }
    }

    // 입찰이 도는 중에 뒤 무리를 붙이고, 그 구간을 가를 시각과 앞 무리가 받은 사람 수 프레임 증가분을 남긴다
    // 앞 무리 기준으로 세는 것이 요점이다, 뒤 무리가 받은 것은 자기 입장 비용이지 남에게 준 부담이 아니다
    private Surge joinLate(long auctionId, List<String> cookies, Audience early,
                           ExecutorService threads, HttpClient http) throws Exception {
        if (cookies.isEmpty()) {
            return Surge.none();
        }

        Thread.sleep(LATE_AFTER.toMillis());

        long startedAt = System.nanoTime();

        // 붙기 직전의 값만 여기서 챙긴다, 뒤엣값은 배달이 끝난 회차 말미에 리포트가 잰다
        List<Subscription> earlyOnly = List.copyOf(early.subscriptions());
        long viewerFramesBefore = viewerFrames(earlyOnly);

        Audience late = openSubscriptions(auctionId, cookies, threads, http);

        long endedAt = System.nanoTime();

        early.subscriptions().addAll(late.subscriptions());
        early.connected().addAndGet(late.connected().get());

        return new Surge(startedAt, endedAt, earlyOnly, viewerFramesBefore, late.connected().get());
    }

    // 가격이 없는 이벤트가 곧 사람 수 프레임이다, 입찰 방송은 가격을 실어 나가므로 안 섞인다
    private long viewerFrames(List<Subscription> subscriptions) {
        return subscriptions.stream().mapToLong(subscription -> subscription.skipped().get()).sum();
    }

    // 헤더를 받은 것과 실제로 읽고 있는 것은 다르다. 첫 현황을 받은 구독만 세고, 그 수가 찰 때까지 기다린다
    // 헤더만 보고 넘어가면 아직 읽기 시작하지 않은 구독을 향해 입찰이 돌아 방송이 통째로 새어 나간다
    private Audience openSubscriptions(long auctionId, List<String> cookies, ExecutorService threads, HttpClient http)
            throws Exception {
        List<Subscription> subscriptions = new ArrayList<>();
        CountDownLatch reading = new CountDownLatch(cookies.size());
        AtomicLong connected = new AtomicLong();

        for (int i = 0; i < cookies.size(); i++) {
            Subscription subscription = new Subscription(i);
            subscriptions.add(subscription);

            String cookie = cookies.get(i);
            threads.submit(() -> read(auctionId, cookie, subscription, reading, connected, http));
        }

        if (!reading.await(30, TimeUnit.SECONDS)) {
            System.out.printf("경고: 첫 현황을 받은 구독이 %d 개에 못 미친다, 이 회차 수치는 조건과 어긋난다%n",
                    cookies.size());
        }

        return new Audience(subscriptions, connected);
    }

    // 느리게 읽거나 아예 안 읽는 구독이다. 수신 버퍼가 차면 서버 쓰기가 막혀 방송이 그 앞에서 멈춘다
    // 속도를 0 으로 두면 한 바이트도 안 읽는데, 그건 실제 사용자가 아니라 상한을 보는 조건이다
    private List<StalledReader> openStalledSubscriptions(long auctionId, List<String> cookies,
                                                         ExecutorService threads) throws IOException {
        List<StalledReader> readers = new ArrayList<>();

        for (int i = 0; i < SLOW_SUBSCRIBERS; i++) {
            Socket socket = new Socket();
            socket.setReceiveBufferSize(SLOW_RECEIVE_BUFFER_BYTES);
            socket.connect(new InetSocketAddress("localhost", port));

            OutputStream out = socket.getOutputStream();
            out.write(("""
                    GET /api/auctions/%d/room/stream HTTP/1.1\r
                    Host: localhost:%d\r
                    Accept: text/event-stream\r
                    Cookie: %s\r
                    \r
                    """.formatted(auctionId, port, cookies.get(i))).getBytes(StandardCharsets.UTF_8));
            out.flush();

            StalledReader reader = new StalledReader(socket);
            readers.add(reader);

            if (SLOW_READ_KBPS > 0) {
                threads.submit(reader::drainSlowly);
            }
        }

        return readers;
    }

    // 스프링이 data: 로 시작하는 줄에 JSON 을 싣고 keep-alive 는 : 로 시작한다
    private void read(long auctionId, String cookie, Subscription subscription, CountDownLatch reading,
                      AtomicLong connected, HttpClient http) {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/auctions/" + auctionId + "/room/stream"))
                .header("Cookie", cookie)
                .GET()
                .build();

        AtomicBoolean counted = new AtomicBoolean();

        // ofLines 로 받으면 스트림이 제때 흐르지 않아 스무 구독 중 두셋만 값을 받는다
        // 바이트 스트림을 직접 읽어야 도착하는 대로 한 줄씩 나온다
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                System.out.printf("구독 거절 %d: %s%n", response.statusCode(),
                        new String(response.body().readAllBytes(), StandardCharsets.UTF_8));

                return;
            }

            connected.incrementAndGet();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    // SSE 주석 줄이다, 서버가 연결을 찔러 본 시각이 여기 찍힌다
                    if (line.startsWith(":")) {
                        subscription.heartbeat();

                        continue;
                    }

                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    // 무엇이 왔든 이 구독이 읽고 있다는 뜻이다, 가격만 세면 입찰 시작 전에는 아무도 못 세어 헛기다린다
                    if (counted.compareAndSet(false, true)) {
                        reading.countDown();
                    }

                    long price = currentPrice(line);

                    // 사람 수 이벤트에는 가격이 없다, 가격 수열에 섞으면 실제 가격 뒤에 올 때 가짜 역전이 된다
                    if (price < 0) {
                        subscription.skip();
                        continue;
                    }

                    subscription.add(price);
                }
            }
        } catch (Exception ignored) {
            // 연결에 실패한 구독은 첫 현황도 못 받는다, 위쪽 대기가 시간 초과로 그것을 드러낸다
        }
    }

    // 세 값 중 현재가만 쓴다, Boot 4 가 Jackson 3 으로 옮겨 어느 ObjectMapper 인지 고르지 않아도 된다
    // 안 맞으면 -1 이다, 호출자가 그것을 가격 수열에서 빼고 따로 센다
    // 관측한 가격 범위와 뺀 수를 리포트에 함께 찍어 계측을 검증한다
    private long currentPrice(String line) {
        Matcher matcher = CURRENT_PRICE.matcher(line);

        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    private BidOutcome runBidders(long auctionId, List<String> cookies, ExecutorService threads, HttpClient http)
            throws Exception {
        BidIncrementTable table = bidIncrementService.loadTable();
        CountDownLatch done = new CountDownLatch(BIDDERS);
        AtomicLong accepted = new AtomicLong();
        AtomicLong timeouts = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        long deadline = System.nanoTime() + BIDDING.toNanos();

        // 재는 것이 재는 대상을 흔들지 않도록 입찰자마다 자기 목록에 담고 끝에서 한 번만 합친다
        List<List<BidSample>> elapsedBatches = new CopyOnWriteArrayList<>();

        for (int i = 0; i < BIDDERS; i++) {
            String cookie = cookies.get(i);

            threads.submit(() -> {
                List<BidSample> elapsed = new ArrayList<>();
                long known = START_PRICE;

                while (System.nanoTime() < deadline) {
                    // 한 번의 실패로 그 입찰자가 통째로 빠지면 표본이 조용히 줄어든다, 안에서 잡고 이어간다
                    try {
                        long amount = table.ruleFor(START_PRICE, known).minAmount();
                        long startedAt = System.nanoTime();
                        boolean placed = place(auctionId, cookie, amount, http, timeouts);

                        // 시간 초과도 담는다, 빼면 제일 느린 건이 통계에서 사라져 p99 가 실제보다 좋아 보인다
                        elapsed.add(new BidSample(startedAt, System.nanoTime() - startedAt));

                        if (placed) {
                            accepted.incrementAndGet();
                        }

                        known = amount;
                    } catch (RuntimeException e) {
                        errors.incrementAndGet();
                    }
                }

                elapsedBatches.add(elapsed);
                done.countDown();
            });
        }

        if (!done.await(BIDDING.plusSeconds(30).toSeconds(), TimeUnit.SECONDS)) {
            System.out.println("경고: 입찰 스레드가 시간 안에 끝나지 않았다");
        }

        return new BidOutcome(accepted.get(), timeouts.get(), errors.get(),
                elapsedBatches.stream().flatMap(List::stream).toList());
    }

    // 막힌 방송 뒤에 입찰이 줄을 서면 응답이 안 온다, 시간 제한을 걸어 실험이 멈추지 않게 하고 그 횟수를 센다
    private boolean place(long auctionId, String cookie, long amount, HttpClient http, AtomicLong timeouts) {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/auctions/" + auctionId + "/bids"))
                .header("Cookie", cookie)
                .header("Content-Type", "application/json")
                .timeout(BID_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":%d}".formatted(amount)))
                .build();

        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() < 300;
        } catch (IOException e) {
            timeouts.incrementAndGet();

            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return false;
        }
    }

    private List<String> logins(String prefix, int count) {
        List<String> cookies = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            cookies.add(login(prefix + i));
        }

        return cookies;
    }

    private String login(String name) {
        User user = users.user(name, Role.GENERAL);
        String token = UUID.randomUUID().toString();
        sessions.seed(token, user.getId(), Role.GENERAL);

        return "RACE_SESSION=" + token;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private long report(int run, Audience audience, BidOutcome outcome, long slowBytes, Surge surge) {
        Tally tally = tally(audience.subscriptions());
        Elapsed elapsed = Elapsed.of(elapsedIn(outcome.samples(), 0, Long.MAX_VALUE));

        // 구독마다 같은 방송을 받으므로 평균 수신 수가 곧 방송 횟수다
        System.out.printf("%d회차 연결 %d/%d 수신구독 %d 성립입찰 %d 시간초과 %d 오류 %d "
                        + "방송 약 %d 수신 %d 역전 %d 최대낙폭 %,d 가격 %,d~%,d 가격없는data %d%n",
                run, audience.connected().get(), SUBSCRIBERS, tally.receiving(),
                outcome.accepted(), outcome.timeouts(), outcome.errors(),
                tally.events() / Math.max(1, audience.subscriptions().size()), tally.events(),
                tally.inversions(), tally.maxDrop(), tally.lowestPrice(), tally.highestPrice(),
                tally.skipped());

        System.out.printf("%d회차 입찰응답 %d건 p50 %.1fms p95 %.1fms p99 %.1fms 최대 %.1fms%n",
                run, elapsed.count(), millis(elapsed.p50()), millis(elapsed.p95()),
                millis(elapsed.p99()), millis(elapsed.max()));

        // 건수를 함께 찍는다, 0 이면 서버가 안 보낸 것이 아니라 계측이 못 잡은 것이라 간격을 해석하면 안 된다
        System.out.printf("%d회차 heartbeat %d건 구독당 최대간격 %.1fms%n",
                run, heartbeats(audience.subscriptions()),
                millis(maxHeartbeatGapNanos(audience.subscriptions())));

        if (surge.happened()) {
            Elapsed before = Elapsed.of(elapsedIn(outcome.samples(), 0, surge.startedAtNanos()));
            Elapsed after = Elapsed.of(elapsedIn(outcome.samples(), surge.startedAtNanos(), Long.MAX_VALUE));

            long viewerFramesAdded = viewerFrames(surge.earlyOnly()) - surge.viewerFramesBefore();

            System.out.printf("%d회차 뒤늦게 %d개가 %.1fms 동안 붙음, 앞 무리 %d개가 받은 사람수프레임 %,d건%n",
                    run, surge.connected(), millis(surge.endedAtNanos() - surge.startedAtNanos()),
                    surge.earlyOnly().size(), viewerFramesAdded);

            System.out.printf("%d회차 입찰응답 붙기전 %d건 p99 %.1fms, 붙은뒤 %d건 p99 %.1fms%n",
                    run, before.count(), millis(before.p99()), after.count(), millis(after.p99()));
        }

        // 목표보다 훨씬 적으면 서버가 그만큼 못 보낸 것이고, 목표를 넘으면 속도 조절기가 안 먹은 것이다
        if (SLOW_SUBSCRIBERS > 0) {
            System.out.printf("%d회차 느린구독 %d개가 %,d바이트 읽음, 목표 %,d바이트%n",
                    run, SLOW_SUBSCRIBERS, slowBytes,
                    SLOW_READ_KBPS * 1024L * BIDDING.toSeconds() * SLOW_SUBSCRIBERS);
        }

        return tally.inversions();
    }

    // 반쯤 걸친 건은 시작 시각으로 가른다, 끝난 시각으로 가르면 느린 건이 뒤 구간으로 밀려 앞이 좋아 보인다
    private List<Long> elapsedIn(List<BidSample> samples, long fromNanos, long toNanos) {
        return samples.stream()
                .filter(sample -> sample.startedAtNanos() >= fromNanos && sample.startedAtNanos() < toNanos)
                .map(BidSample::elapsedNanos)
                .toList();
    }

    private long heartbeats(List<Subscription> subscriptions) {
        return subscriptions.stream().mapToLong(subscription -> subscription.heartbeatNanos().size()).sum();
    }

    // 구독 하나 안에서 이웃한 두 신호의 간격 중 제일 큰 값, 그중에서도 최악을 돌려준다
    // 스케줄러가 막히면 방 전체가 함께 밀리므로 한 구독만 봐도 되지만, 최악을 보는 편이 놓치지 않는다
    private long maxHeartbeatGapNanos(List<Subscription> subscriptions) {
        long max = 0;

        for (Subscription subscription : subscriptions) {
            List<Long> beats = subscription.heartbeatNanos();

            synchronized (beats) {
                for (int i = 1; i < beats.size(); i++) {
                    max = Math.max(max, beats.get(i) - beats.get(i - 1));
                }
            }
        }

        return max;
    }

    private Tally tally(List<Subscription> subscriptions) {
        long events = 0;
        long receiving = 0;
        long inversions = 0;
        long maxDrop = 0;
        long lowestPrice = Long.MAX_VALUE;
        long highestPrice = Long.MIN_VALUE;
        long skipped = 0;

        for (Subscription subscription : subscriptions) {
            List<Long> prices = subscription.prices();
            skipped += subscription.skipped().get();

            if (!prices.isEmpty()) {
                receiving++;
            }

            for (long price : prices) {
                lowestPrice = Math.min(lowestPrice, price);
                highestPrice = Math.max(highestPrice, price);
            }

            for (int i = 1; i < prices.size(); i++) {
                long drop = prices.get(i - 1) - prices.get(i);

                if (drop > 0) {
                    inversions++;
                    maxDrop = Math.max(maxDrop, drop);
                }
            }

            events += prices.size();
        }

        return new Tally(events, receiving, inversions, maxDrop, lowestPrice, highestPrice, skipped);
    }

    private record Audience(List<Subscription> subscriptions, AtomicLong connected) {
    }

    // 언제 걸린 건인지를 함께 담는다, 안 담으면 뒤늦게 붙는 구간의 앞뒤를 가를 수 없다
    private record BidSample(long startedAtNanos, long elapsedNanos) {
    }

    // 뒤 무리가 붙은 구간이다, startedAtNanos 가 0 이면 이번 회차에는 뒤 무리가 없었다
    private record Surge(long startedAtNanos, long endedAtNanos, List<Subscription> earlyOnly,
                         long viewerFramesBefore, long connected) {

        static Surge none() {
            return new Surge(0, 0, List.of(), 0, 0);
        }

        boolean happened() {
            return startedAtNanos > 0;
        }
    }

    private record BidOutcome(long accepted, long timeouts, long errors, List<BidSample> samples) {
    }

    // 입찰 요청을 보내고 응답을 받기까지 걸린 시간, 시간 초과는 제한 시간만큼 걸린 것으로 들어간다
    private record Elapsed(int count, long p50, long p95, long p99, long max) {

        static Elapsed of(List<Long> nanos) {
            if (nanos.isEmpty()) {
                return new Elapsed(0, 0, 0, 0, 0);
            }

            List<Long> sorted = nanos.stream().sorted().toList();

            return new Elapsed(sorted.size(), at(sorted, 0.50), at(sorted, 0.95), at(sorted, 0.99),
                    sorted.get(sorted.size() - 1));
        }

        // 가장 가까운 순위를 쓴다, 보간하면 표본이 적을 때 실제로 관측되지 않은 값이 나온다
        private static long at(List<Long> sorted, double quantile) {
            int rank = (int) Math.ceil(quantile * sorted.size());

            return sorted.get(Math.min(Math.max(rank - 1, 0), sorted.size() - 1));
        }
    }

    private record Tally(long events, long receiving, long inversions, long maxDrop,
                         long lowestPrice, long highestPrice, long skipped) {
    }

    // 느린 회선을 흉내낸다, 정해진 간격마다 정해진 양만 읽고 쉰다
    // 한 바이트도 안 읽는 것은 실제 사용자가 아니라서, 어느 속도부터 무너지는지 이것으로 찾는다
    private static final class StalledReader {

        private final Socket socket;
        private final AtomicLong bytesRead = new AtomicLong();

        private StalledReader(Socket socket) {
            this.socket = socket;
        }

        private long bytesRead() {
            return bytesRead.get();
        }

        private void drainSlowly() {
            int perTick = Math.max(1, (int) (SLOW_READ_KBPS * 1024L * SLOW_READ_TICK_MILLIS / 1000L));
            byte[] buffer = new byte[Math.min(perTick, SLOW_RECEIVE_BUFFER_BYTES)];

            try {
                InputStream in = socket.getInputStream();

                while (!socket.isClosed()) {
                    // 수신 버퍼가 작아 한 번에 그만큼밖에 안 나온다, 이번 몫을 채울 때까지 여러 번 읽는다
                    // available 로 물어보고 읽어야 다음 조각을 기다리다 이번 간격을 넘기지 않는다
                    int taken = 0;

                    while (taken < perTick && in.available() > 0) {
                        int read = in.read(buffer, 0, Math.min(buffer.length, perTick - taken));

                        if (read < 0) {
                            return;
                        }

                        taken += read;
                    }

                    bytesRead.addAndGet(taken);
                    Thread.sleep(SLOW_READ_TICK_MILLIS);
                }
            } catch (IOException e) {
                // 정리하며 소켓을 닫으면 여기로 온다, 실험이 끝나는 정상 경로다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 실험 정리라 실패해도 할 일이 없다
            }
        }
    }

    // 읽는 쪽은 스레드 하나뿐이고 리포트는 그 스레드가 끝난 뒤에 읽는다
    // 쓸 때마다 배열을 복사하면 이벤트 수의 제곱이 되어, 구독을 올렸을 때 읽는 쪽이 스스로 느려진다
    private record Subscription(int id, List<Long> prices, AtomicLong skipped, List<Long> heartbeatNanos) {

        Subscription(int id) {
            this(id, Collections.synchronizedList(new ArrayList<>()), new AtomicLong(),
                    Collections.synchronizedList(new ArrayList<>()));
        }

        // sweepClosedSubscriptions 가 실제로 돈 시각이다, 스케줄러가 밀리면 간격이 벌어진다
        void heartbeat() {
            heartbeatNanos.add(System.nanoTime());
        }

        void add(long price) {
            prices.add(price);
        }

        void skip() {
            skipped.incrementAndGet();
        }
    }
}
