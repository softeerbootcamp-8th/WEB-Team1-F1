package com.softeer.race.notification.application;

/**
 * 알림을 받아가는 열린 구독
 */
public interface UserSubscriber {

    /**
     * 새 알림 전송, 이미 닫혔으면 조용히 버린다
     */
    void send(NotificationPush push);

    /**
     * 안 읽은 건수만 전송, 연결 직후 화면의 배지를 맞추는 데 쓴다
     * <p>
     * 채널이 아니라 구독 하나를 상대로 부른다. 방금 연결한 화면만 배지가 비어 있고, 이미 열려
     * 있던 다른 탭은 자기 배지를 맞춰 둔 상태라 같은 값을 다시 받을 이유가 없다.
     */
    void sendUnreadCount(long unreadCount);

    /**
     * 살아 있는지 확인만 하는 신호, 내용을 담지 않는다
     */
    void ping();

    /**
     * 아직 살아 있는지, 채널이 닫힌 구독을 걷어낼 때 묻는다
     */
    boolean isOpen();

    /**
     * 서버가 연결을 끝낸다, 이미 끝났으면 아무 일도 없고 던지지 않는다
     */
    // 던지지 않는 것이 계약이다, 그래야 채널이 방어 없이 걷어낸 구독을 한꺼번에 끝낼 수 있다
    void close();
}