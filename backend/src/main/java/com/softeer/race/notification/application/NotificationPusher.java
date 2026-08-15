package com.softeer.race.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 저장이 확정된 알림을 열려 있는 구독으로 밀어준다
 * <p>
 * <b>커밋 뒤에 보내는 이유.</b> 저장과 같은 트랜잭션에서 보내면 두 군데가 어긋난다. 회원이 알림을
 * 보고 목록을 열었을 때 그 알림이 아직 없고, 저장이 롤백되면 화면에만 남는다. 게다가 전송은 열린
 * 연결에 직접 쓰는 일이라 트랜잭션 안에 두면 안 받아 가는 상대 하나가 DB 커넥션을 붙잡는다.
 * RoomStreamService 가 브로드캐스트에 {@code @Transactional} 을 붙이지 않은 것과 같은 이유다.
 * <p>
 * <b>{@code fallbackExecution} 을 켜지 않는다.</b> 트랜잭션 없이 발행하면 이 리스너는 조용히 안 불린다.
 * 그걸 켜서 구제하면 "커밋 뒤에만 보낸다"는 보장이 깨지고, 트랜잭션 밖에서 발행한 버그가 정상 동작으로
 * 위장된다. 안 불려서 드러나는 쪽이 낫다.
 * <p>
 * <b>여기서 DB 를 읽거나 쓰지 않는다.</b> 이 시점의 트랜잭션은 이미 커밋됐고 자원만 스레드에 남아
 * 있어서, {@code @Transactional}(REQUIRED) 로 쓰면 끝난 트랜잭션에 참여해 반영되지 않을 수 있다.
 * 밀어줄 내용은 발행 쪽이 트랜잭션 안에서 다 담아 보내므로 여기서 조회할 것이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPusher {

    private final UserChannel userChannel;

    /**
     * phase 는 기본값 AFTER_COMMIT 이다. 커밋한 <b>같은 스레드에서 동기로</b> 돈다 — 커밋 뒤라는
     * 말이 다른 스레드라는 뜻이 아니다. 낙찰 알림이라면 경매 진행 스케줄러 스레드가 이 전송을
     * 수행하므로 전송이 늦으면 다음 틱이 밀린다. 수신자가 회원 한 명(열린 탭 수)이고 내용이 한 줄이라
     * 지금은 감당하고, 한 사건이 여러 회원에게 가는 구조가 되면 별도 스레드로 뺄지 다시 본다.
     */
    @TransactionalEventListener
    public void push(NotificationPublished event) {
        // 예외를 밖으로 내지 않는다.
        // 커밋 후 콜백에서 던진 예외는 커밋을 되돌리지 못하지만 commit() 호출자에게는 그대로 올라간다.
        // 그러면 이미 확정된 낙찰이 "종료 전이 실패"로 로깅되고 다음 주기에 다시 잡힌다.
        // 알림 전달 실패가 업무 트랜잭션의 성패를 흔들어선 안 된다 — 알림은 저장돼 있고 다음 접속의
        // 조회가 진실을 준다.
        try {
            userChannel.send(event.userId(), event.push());
        } catch (Exception e) {
            log.warn("알림 전송 실패, 회원 {} 알림 {}", event.userId(), event.push().notification().id(), e);
        }
    }

    /**
     * 읽음이 바뀐 회원의 열려 있는 화면들에 건수를 맞춰 준다
     * <p>
     * 위 {@link #push} 와 나눠 둔다. 실어 보낼 것도 받는 화면이 할 일도 달라서다 — 새 알림은
     * 목록에 줄을 더하지만 이쪽은 이미 있던 줄의 읽음 표시를 고친다.
     */
    @TransactionalEventListener
    public void pushUnreadCount(UnreadCountChanged event) {
        // push 와 같은 이유로 삼킨다, 건수 전송 실패가 읽음 처리를 실패로 만들면 안 된다
        try {
            userChannel.sendUnreadCount(event.userId(), event.unreadCount());
        } catch (Exception e) {
            log.warn("안 읽은 건수 전송 실패, 회원 {}", event.userId(), e);
        }
    }
}