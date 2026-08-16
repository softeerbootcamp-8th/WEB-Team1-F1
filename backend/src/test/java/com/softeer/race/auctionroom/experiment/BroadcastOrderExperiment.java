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
import org.springframework.test.context.jdbc.Sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql("/sql/bid-increment-bands.sql")
// 이름이 Test 로 끝나지 않는 것이 의도다, 초록이 아무것도 보장하지 않아 테스트로 읽히면 안 된다
@SuppressWarnings("JUnitTestClassNamingConvention")
class BroadcastOrderExperiment extends IntegrationTestSupport {

    private static final int SUBSCRIBERS = Integer.getInteger("subscribers", 10);
    private static final int SLOW_SUBSCRIBERS = Integer.getInteger("slow", 0);
    private static final int BIDDERS = Integer.getInteger("bidders", 5);
    private static final int RUNS = Integer.getInteger("runs", 1);
    private static final Duration BIDDING = Duration.ofSeconds(Integer.getInteger("seconds", 20));

    private static final Duration BID_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(10);
    private static final Duration DRAIN = Duration.ofSeconds(3);
    private static final long START_PRICE = 10_000_000L;

    // 작게 잡을수록 빨리 찬다, 안 읽는 구독자의 수신 버퍼가 차야 서버 쓰기가 막힌다
    private static final int SLOW_RECEIVE_BUFFER_BYTES = 4096;

    private static final Pattern CURRENT_PRICE = Pattern.compile("\"currentPrice\"\\s*:\\s*(\\d+)");

    @LocalServerPort
    private int port;

    @Autowired
    private BidIncrementService bidIncrementService;

    @Test
    @DisplayName("동시 입찰 중 구독자가 받은 현재가 수열에 역전이 있는지 센다")
    void countPriceInversions() throws Exception {
        System.out.printf("조건 구독 %d 안읽는구독 %d 입찰자 %d 회차 %d 구간 %d초%n",
                SUBSCRIBERS, SLOW_SUBSCRIBERS, BIDDERS, RUNS, BIDDING.toSeconds());

        long totalInversions = 0;

        for (int run = 1; run <= RUNS; run++) {
            // 앞 회차의 연결이 서버에 남은 채 다음 회차가 시작하면 표본이 오염된다
            // 정리는 채널 sweep 이 하므로 그 주기보다 넉넉히 쉰다
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
        List<Socket> stalled = new ArrayList<>();

        // 계정과 세션을 전부 먼저 만든다. 시더가 전역 TestClock 을 과거로 고정했다 푸는데,
        // 연결이 열린 뒤에 부르면 그 사이 서버가 과거 시각으로 단계를 판정해 구독을 거절한다
        List<String> watchers = logins("구독자", SUBSCRIBERS);
        List<String> stallers = logins("안읽는구독자", SLOW_SUBSCRIBERS);
        List<String> bidders = logins("입찰자", BIDDERS);

        try {
            Audience audience = openSubscriptions(auctionId, watchers, threads, http);
            stalled.addAll(openStalledSubscriptions(auctionId, stallers));

            BidOutcome outcome = runBidders(auctionId, bidders, threads, http);

            // 막힌 소켓을 먼저 닫아 서버 쓰기를 풀어준다
            // 안 그러면 거기 걸려 있던 입찰이 테이블 정리 뒤에 깨어나 없는 회원을 참조한다
            stalled.forEach(this::closeQuietly);
            stalled.clear();

            // 마지막 방송이 도착할 틈을 준다, 곧바로 끊으면 보낸 것과 받은 것이 어긋난다
            Thread.sleep(DRAIN.toMillis());

            http.shutdownNow();
            threads.shutdownNow();

            if (!threads.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("경고: 구독 읽기 스레드가 시간 안에 끝나지 않았다, 뒤쪽 표본이 빠졌을 수 있다");
            }

            return report(run, audience, outcome);
        } finally {
            stalled.forEach(this::closeQuietly);
        }
    }

    // 헤더를 받은 것과 실제로 읽고 있는 것은 다르다. 첫 현황을 받은 구독만 세고, 그 수가 찰 때까지 기다린다
    // 헤더만 보고 넘어가면 아직 읽기 시작하지 않은 구독을 향해 입찰이 돌아 방송이 통째로 새어 나간다
    private Audience openSubscriptions(long auctionId, List<String> cookies, ExecutorService threads, HttpClient http)
            throws Exception {
        List<Subscription> subscriptions = new ArrayList<>();
        CountDownLatch reading = new CountDownLatch(SUBSCRIBERS);
        AtomicLong connected = new AtomicLong();

        for (int i = 0; i < SUBSCRIBERS; i++) {
            Subscription subscription = new Subscription(i);
            subscriptions.add(subscription);

            String cookie = cookies.get(i);
            threads.submit(() -> read(auctionId, cookie, subscription, reading, connected, http));
        }

        if (!reading.await(30, TimeUnit.SECONDS)) {
            System.out.printf("경고: 첫 현황을 받은 구독이 %d 개에 못 미친다, 이 회차 수치는 조건과 어긋난다%n",
                    SUBSCRIBERS);
        }

        return new Audience(subscriptions, connected.get());
    }

    // 붙기만 하고 한 바이트도 안 읽는 구독이다. 수신 버퍼가 차면 서버 쓰기가 막혀 방송이 그 앞에서 멈춘다
    // 모바일 네트워크나 백그라운드 탭이 실제로 만드는 상황이고, 로컬 루프백이 오히려 실제와 먼 조건이다
    private List<Socket> openStalledSubscriptions(long auctionId, List<String> cookies) throws IOException {
        List<Socket> sockets = new ArrayList<>();

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

            sockets.add(socket);
        }

        return sockets;
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
        List<List<Long>> elapsedBatches = new CopyOnWriteArrayList<>();

        for (int i = 0; i < BIDDERS; i++) {
            String cookie = cookies.get(i);

            threads.submit(() -> {
                List<Long> elapsed = new ArrayList<>();
                long known = START_PRICE;

                while (System.nanoTime() < deadline) {
                    // 한 번의 실패로 그 입찰자가 통째로 빠지면 표본이 조용히 줄어든다, 안에서 잡고 이어간다
                    try {
                        long amount = table.ruleFor(START_PRICE, known).minAmount();
                        long startedAt = System.nanoTime();
                        boolean placed = place(auctionId, cookie, amount, http, timeouts);

                        // 시간 초과도 담는다, 빼면 제일 느린 건이 통계에서 사라져 p99 가 실제보다 좋아 보인다
                        elapsed.add(System.nanoTime() - startedAt);

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

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 실험 정리라 실패해도 할 일이 없다
        }
    }

    private long report(int run, Audience audience, BidOutcome outcome) {
        Tally tally = tally(audience.subscriptions());
        Elapsed elapsed = Elapsed.of(outcome.elapsedNanos());

        // 구독마다 같은 방송을 받으므로 평균 수신 수가 곧 방송 횟수다
        System.out.printf("%d회차 연결 %d/%d 수신구독 %d 성립입찰 %d 시간초과 %d 오류 %d "
                        + "방송 약 %d 수신 %d 역전 %d 최대낙폭 %,d 가격 %,d~%,d 가격없는data %d%n",
                run, audience.connected(), SUBSCRIBERS, tally.receiving(),
                outcome.accepted(), outcome.timeouts(), outcome.errors(),
                tally.events() / Math.max(1, audience.subscriptions().size()), tally.events(),
                tally.inversions(), tally.maxDrop(), tally.lowestPrice(), tally.highestPrice(),
                tally.skipped());

        System.out.printf("%d회차 입찰응답 %d건 p50 %.1fms p95 %.1fms p99 %.1fms 최대 %.1fms%n",
                run, elapsed.count(), millis(elapsed.p50()), millis(elapsed.p95()),
                millis(elapsed.p99()), millis(elapsed.max()));

        return tally.inversions();
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

    private record Audience(List<Subscription> subscriptions, long connected) {
    }

    private record BidOutcome(long accepted, long timeouts, long errors, List<Long> elapsedNanos) {
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

    // 읽는 쪽은 스레드 하나뿐이고 리포트는 그 스레드가 끝난 뒤에 읽는다
    // 쓸 때마다 배열을 복사하면 이벤트 수의 제곱이 되어, 구독을 올렸을 때 읽는 쪽이 스스로 느려진다
    private record Subscription(int id, List<Long> prices, AtomicLong skipped) {

        Subscription(int id) {
            this(id, Collections.synchronizedList(new ArrayList<>()), new AtomicLong());
        }

        void add(long price) {
            prices.add(price);
        }

        void skip() {
            skipped.incrementAndGet();
        }
    }
}
