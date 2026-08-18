package com.softeer.race.auctionlist.experiment;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 실험이지 테스트가 아니다. 초록이 아무것도 보장하지 않고 재는 것은 출력된 수치다.
// 기본 test 태스크에서 제외하고 아래로 따로 돌린다. 경매방 실험까지 같이 돌지 않게 이름을 건다.
//   ./gradlew experiment --tests "*AuctionListBroadcastExperiment*" -Psubscribers=10 -Pslow=3
//
// 재는 것 둘이다. 회선이 느린 목록 구독자가 있을 때 남은 구독의 카드가 제때 오는지,
// 그리고 그때 경매를 처리한 입찰 요청의 응답이 늘어지는지다. 이슈 #449 의 도입 1번과 2번이 이 둘이다.
@Tag("experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "race.scheduling.enabled=true")
@Sql("/sql/bid-increment-bands.sql")
@SuppressWarnings("JUnitTestClassNamingConvention")
class AuctionListBroadcastExperiment extends IntegrationTestSupport {

    private static final int SUBSCRIBERS = Integer.getInteger("subscribers", 10);

    // 열어 두고 한 바이트도 안 읽는 구독이다, 서버 쓰기가 소켓 버퍼에서 막히는 상황을 만든다
    private static final int SLOW_SUBSCRIBERS = Integer.getInteger("slow", 3);
    private static final int BIDDERS = Integer.getInteger("bidders", 3);
    private static final Duration BIDDING = Duration.ofSeconds(Integer.getInteger("seconds", 15));

    private static final Duration BID_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DRAIN = Duration.ofSeconds(3);
    private static final long START_PRICE = 10_000_000L;

    // 작게 잡아야 몇 프레임 만에 버퍼가 찬다, 크면 실험 시간 안에 막히지 않는다
    private static final int SLOW_RECEIVE_BUFFER_BYTES = 4096;

    private static final Pattern CURRENT_PRICE = Pattern.compile("\"currentPrice\"\\s*:\\s*(\\d+)");

    @LocalServerPort
    private int port;

    @Autowired
    private BidIncrementService bidIncrementService;

    @Test
    @DisplayName("느린 목록 구독자가 있을 때 남은 구독의 갱신과 입찰 응답을 잰다")
    void measureSlowSubscriberImpact() throws Exception {
        // 상태가 아니라 시각으로 단계를 판정하므로 시작 시각만 과거면 진행 중이다
        LocalDateTime startAt = LocalDateTime.now().minusMinutes(1);
        User seller = users.user("판매자", Role.GENERAL);
        long auctionId = rooms.room(seller, startAt).startPrice(START_PRICE).create();

        // 시더가 전역 TestClock 을 과거로 고정했다 푸는데, 연결이 열린 뒤에 부르면 그 사이 판정이 흔들린다
        List<String> bidders = logins("입찰자", BIDDERS);

        ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
        HttpClient http = HttpClient.newBuilder().executor(threads).build();
        List<StalledReader> stalled = new ArrayList<>();

        try {
            List<Watcher> watchers = openWatchers(threads, http);
            stalled.addAll(openStalledWatchers());

            BidOutcome outcome = runBidders(auctionId, bidders, threads, http);

            // 안 읽는 소켓에 실제로 얼마나 쌓였는지 본다, 0 이면 구독이 안 붙은 것이고
            // 아주 크면 커널 버퍼가 다 삼켜서 서버 쓰기가 막히지 않은 것이라 조건이 안 선 것이다
            long buffered = stalled.stream().mapToLong(StalledReader::buffered).sum();

            // 막힌 소켓을 먼저 닫아 서버 쓰기를 풀어준다, 안 그러면 거기 걸린 배달이 정리 뒤에 깨어난다
            stalled.forEach(StalledReader::close);
            stalled.clear();

            // 마지막 방송이 도착할 틈을 준다, 곧바로 끊으면 보낸 것과 받은 것이 어긋난다
            Thread.sleep(DRAIN.toMillis());

            http.shutdownNow();
            threads.shutdownNow();

            report(watchers, outcome, buffered);
        } finally {
            stalled.forEach(StalledReader::close);
        }
    }

    private List<Watcher> openWatchers(ExecutorService threads, HttpClient http) throws Exception {
        List<Watcher> watchers = new ArrayList<>();
        CountDownLatch opened = new CountDownLatch(SUBSCRIBERS);

        for (int i = 0; i < SUBSCRIBERS; i++) {
            Watcher watcher = new Watcher();
            watchers.add(watcher);

            threads.submit(() -> read(watcher, opened, http));
        }

        if (!opened.await(20, TimeUnit.SECONDS)) {
            System.out.println("경고: 목록 구독이 시간 안에 다 열리지 않았다");
        }

        return watchers;
    }

    // 스프링이 data: 로 시작하는 줄에 JSON 을 싣고 keep-alive 는 : 로 시작한다
    private void read(Watcher watcher, CountDownLatch opened, HttpClient http) {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/auctions/stream"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        try (InputStream body = http.send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            opened.countDown();

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    watcher.sawCard(currentPrice(line));
                }
            }
        } catch (IOException | InterruptedException e) {
            // 실험이 끝나며 끊는 것이 정상 경로다, 여기서 세울 것이 없다
            opened.countDown();
        }
    }

    private List<StalledReader> openStalledWatchers() throws IOException {
        List<StalledReader> readers = new ArrayList<>();

        for (int i = 0; i < SLOW_SUBSCRIBERS; i++) {
            Socket socket = new Socket();
            socket.setReceiveBufferSize(SLOW_RECEIVE_BUFFER_BYTES);
            socket.connect(new InetSocketAddress("localhost", port));

            OutputStream out = socket.getOutputStream();
            out.write(("""
                    GET /api/auctions/stream HTTP/1.1\r
                    Host: localhost:%d\r
                    Accept: text/event-stream\r
                    \r
                    """.formatted(port)).getBytes(StandardCharsets.UTF_8));
            out.flush();

            readers.add(new StalledReader(socket));
        }

        return readers;
    }

    private BidOutcome runBidders(long auctionId, List<String> cookies, ExecutorService threads, HttpClient http)
            throws Exception {
        BidIncrementTable table = bidIncrementService.loadTable();
        CountDownLatch done = new CountDownLatch(BIDDERS);
        AtomicLong accepted = new AtomicLong();
        AtomicLong timeouts = new AtomicLong();
        AtomicLong highest = new AtomicLong(START_PRICE);
        long deadline = System.nanoTime() + BIDDING.toNanos();

        List<List<Long>> elapsedBatches = new CopyOnWriteArrayList<>();

        for (int i = 0; i < BIDDERS; i++) {
            String cookie = cookies.get(i);

            threads.submit(() -> {
                List<Long> elapsed = new ArrayList<>();
                long known = START_PRICE;

                while (System.nanoTime() < deadline) {
                    long amount = table.ruleFor(START_PRICE, known).minAmount();
                    long startedAt = System.nanoTime();
                    boolean placed = place(auctionId, cookie, amount, http, timeouts);

                    // 시간 초과도 담는다, 빼면 제일 느린 건이 통계에서 사라져 값이 실제보다 좋아 보인다
                    elapsed.add(System.nanoTime() - startedAt);

                    if (placed) {
                        accepted.incrementAndGet();
                        highest.accumulateAndGet(amount, Math::max);
                    }

                    known = amount;
                }

                elapsedBatches.add(elapsed);
                done.countDown();
            });
        }

        if (!done.await(BIDDING.plusSeconds(30).toSeconds(), TimeUnit.SECONDS)) {
            System.out.println("경고: 입찰 스레드가 시간 안에 끝나지 않았다");
        }

        return new BidOutcome(accepted.get(), timeouts.get(), highest.get(),
                elapsedBatches.stream().flatMap(List::stream).sorted().toList());
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

    private void report(List<Watcher> watchers, BidOutcome outcome, long buffered) {
        long cards = watchers.stream().mapToLong(Watcher::cards).sum();
        long upToDate = watchers.stream().filter(watcher -> watcher.lastPrice() == outcome.highest()).count();
        double worstGap = watchers.stream().mapToDouble(watcher -> millis(watcher.maxGapNanos())).max().orElse(0);

        System.out.printf("""
                        [목록 배달 실험] 구독 %d, 안 읽는 구독 %d, 입찰자 %d, %d초
                        입찰 수락 %d, 시간 초과 %d, 최종가 %d
                        입찰 응답 p50 %.1fms p95 %.1fms 최대 %.1fms
                        정상 구독이 받은 카드 %d, 최종가까지 따라온 구독 %d/%d
                        정상 구독의 카드 도착 최대 간격 %.1fms
                        안 읽는 구독에 쌓인 바이트 %d
                        %n""",
                SUBSCRIBERS, SLOW_SUBSCRIBERS, BIDDERS, BIDDING.toSeconds(),
                outcome.accepted(), outcome.timeouts(), outcome.highest(),
                millis(outcome.at(0.50)), millis(outcome.at(0.95)), millis(outcome.max()),
                cards, upToDate, SUBSCRIBERS,
                worstGap, buffered);
    }

    private long currentPrice(String line) {
        Matcher matcher = CURRENT_PRICE.matcher(line);

        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
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

    // 구독 하나가 받은 것, 읽는 스레드 하나만 건드리고 끝에서 실험 스레드가 읽는다
    private static final class Watcher {

        private volatile long cards;
        private volatile long lastPrice;
        private volatile long lastCardNanos;
        private volatile long maxGapNanos;

        void sawCard(long price) {
            long now = System.nanoTime();

            if (lastCardNanos != 0) {
                maxGapNanos = Math.max(maxGapNanos, now - lastCardNanos);
            }

            lastCardNanos = now;
            cards++;

            if (price > 0) {
                lastPrice = price;
            }
        }

        long cards() {
            return cards;
        }

        long lastPrice() {
            return lastPrice;
        }

        long maxGapNanos() {
            return maxGapNanos;
        }
    }

    // 열어만 두고 읽지 않는다, 서버가 쓰다가 소켓 버퍼에서 막히게 만드는 것이 목적이다
    private static final class StalledReader {

        private final Socket socket;

        private StalledReader(Socket socket) {
            this.socket = socket;
        }

        long buffered() {
            try {
                return socket.getInputStream().available();
            } catch (IOException e) {
                return -1;
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException e) {
                // 이미 닫혔으면 할 일이 없다
            }
        }
    }

    private record BidOutcome(long accepted, long timeouts, long highest, List<Long> elapsedNanos) {

        long at(double quantile) {
            if (elapsedNanos.isEmpty()) {
                return 0;
            }

            int index = (int) Math.min(elapsedNanos.size() - 1L, Math.round(quantile * (elapsedNanos.size() - 1)));

            return elapsedNanos.get(index);
        }

        long max() {
            return elapsedNanos.isEmpty() ? 0 : elapsedNanos.get(elapsedNanos.size() - 1);
        }
    }
}
