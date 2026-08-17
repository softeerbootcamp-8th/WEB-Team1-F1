package com.softeer.race.notification.application;

import com.softeer.race.common.config.SchedulingConfig;
import com.softeer.race.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.softeer.race.notification.application.NotificationDeliveryMetrics.Event.UNREAD_COUNT;

/**
 * 회원 알림을 열려 있는 구독으로 흘려보내는 서비스
 * <p>
 * 경매방과 달리 구독 등록 전에 검증하는 것이 없다. 인증을 통과한 회원이 자기 채널을 구독하는 것이라
 * 없는 방·닫힌 방처럼 판정할 대상이 애초에 없다.
 */
// @Transactional 을 붙이지 않는다, 전송이 소켓 쓰기라 안 받아 가는 상대 하나에 커넥션이 묶인다
@Service
@RequiredArgsConstructor
public class NotificationStreamService {

    // 10초마다 열린 구독을 찔러 보고(하트비트) 죽은 구독을 걷어낸다.
    // CloudFront 가 응답 패킷 사이의 침묵을 30초까지만 허용한다(오리진 응답 시간 초과 기본값).
    // 알림은 몇 시간 조용한 것이 정상이라, 하트비트가 없으면 30초마다 끊기고 열려 있던 연결이 모두
    // 재접속하면서 재연결마다 세션 조회와 건수 조회가 붙는다. 3배 여유는 한 틱 밀려도 한도에 안 닿게.
    // 경매방 sweep 이 5초인 것은 나간 사람이 접속자 수에서 빠지는 시간이라 목적이 다르다.
    private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000L;

    private final UserChannel userChannel;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryMetrics deliveryMetrics;

    /**
     * 구독을 등록하고, 그 구독에만 안 읽은 건수를 한 번 보낸다
     * <p>
     * 등록이 먼저다. 건수를 세는 동안 도착한 알림은 구독이 없으면 통째로 유실되는데, 이 순서에서
     * 최악은 배지가 한 건 낮게 덮이는 것이고 다음 알림에서 맞춰진다.
     */
    public void subscribe(long userId, UserSubscriber subscriber) {
        userChannel.subscribe(userId, subscriber);

        try {
            // 채널이 아니라 이 구독에만 보낸다, 이미 열려 있던 다른 탭은 자기 배지를 맞춰 둔 상태다
            long unreadCount = notificationRepository.countUnread(userId);
            deliveryMetrics.recordSend(
                    UNREAD_COUNT,
                    () -> subscriber.sendUnreadCount(unreadCount),
                    subscriber::isOpen);
        } catch (RuntimeException e) {
            // 등록은 됐는데 응답이 시작되지 못한 구독은 청소로도 걷어낼 수 없다. 컨트롤러가 예외로
            // 끝나면 해제 콜백이 붙을 기회가 없고, 초기화 전 emitter 는 찔러 봐도 예외를 내지 않는다.
            userChannel.unsubscribe(userId, subscriber);
            throw e;
        }
    }

    /**
     * 구독을 빼낸다
     * <p>
     * 경매방과 달리 남은 구독에 아무것도 보내지 않는다. 접속자 수처럼 남이 보는 값이 없다.
     */
    public void unsubscribe(long userId, UserSubscriber subscriber) {
        userChannel.unsubscribe(userId, subscriber);
    }

    /**
     * 죽은 구독을 걷어내고 살아 있는 구독을 찔러 본다
     * <p>
     * 회원별 try/catch 가 없다. 경매방 sweep 은 정리 뒤에 현황을 다시 조회해 보내기 때문에 한 방의
     * 실패가 나머지 방을 막을 수 있었지만, 여기는 찔러 보기만 하고 그 실패는 구독 자신이 삼킨다.
     */
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MILLIS, scheduler = SchedulingConfig.NOTIFICATION_STREAM)
    public void sweepClosedSubscriptions() {
        userChannel.sweepClosed();
    }
}
