package com.softeer.race.notification.application;
/**
 * 회원별로 열려 있는 구독을 모아 두고 알림을 흘려보내는 채널
 * <p>
 * 경매방 채널과 나눠 둔다. 방은 열려 있는 구독 수가 접속자 수로 화면에 나가지만, 회원 채널은
 * 구독 수에 업무 의미가 없고(탭을 두 개 열면 둘) 전달 단위가 방이 아니라 회원 한 명이다.
 * 무엇보다 <b>경매방을 보고 있지 않은 회원도 알림을 받아야 한다.</b>
 */
public interface UserChannel {
    /**
     * 구독을 회원에게 등록한다, 같은 구독을 두 번 등록해도 하나다
     */
    void subscribe(long userId, UserSubscriber subscriber);

    /**
     * 구독을 빼낸다, 같은 구독을 두 번 빼도 결과는 같다
     */
    void unsubscribe(long userId, UserSubscriber subscriber);

    /**
     * 이 회원의 열려 있는 모든 구독에 전송, 닫힌 구독은 순회가 끝난 뒤 걷어낸다
     * <p>
     * 구독이 없으면 아무 일도 하지 않는다. 접속하지 않은 회원에게 보내는 것은 실패가 아니다 —
     * 알림은 이미 저장돼 있고 다음 접속의 조회가 진실을 준다.
     */
    void send(long userId, NotificationPush push);

    /**
     * 모든 구독을 찔러 보고 닫힌 것을 걷어낸다
     * <p>
     * 방 채널과 달리 걷어낸 대상을 돌려주지 않는다. 방은 사람이 빠지면 남은 사람이 보는 접속자
     * 수가 달라져 다시 브로드캐스트해야 하지만, 회원 채널에는 남은 구독에 알려 줄 변화가 없다.
     */
    void sweepClosed();
}
