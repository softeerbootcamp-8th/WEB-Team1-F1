package com.softeer.race.notification.domain;

/**
 * 알림 종류
 * <p>
 * 문구와 이동할 곳을 종류가 함께 들고 있다. 알림을 일으키는 쪽마다 문구를 조립하면 같은 종류가
 * 자리마다 다르게 보이고, 종류가 늘 때 이동할 곳 정하기를 빠뜨릴 수 있다. 여기에 상수를 추가하면
 * 둘 다 강제로 따라온다.
 */
public enum NotificationType {

    // 가리킬 대상이 없는 유일한 알림이다. 회원 자신을 참조로 잡으면 주소에 회원 식별자가 실려서
    // 남의 식별자를 넣은 주소가 성립하는 통로가 생기므로, 참조 없이 고정 화면으로 보낸다.
    // 홈이 아니라 경매 목록인 이유 — 가입 성공 직후 화면이 이미 홈이라 홈으로 보내면 눌러도
    // 제자리다. 판매 온보딩이나 마이페이지가 실체를 갖추면 목적지를 다시 본다
    WELCOME("환영합니다! 진행 중인 경매에 참여해 보세요.", "/auctions"),


    // TODO 평가 결과에서 갈 곳은 E-8(평가 승인 알림 연결)에서 확정한다, 지금은 판매 신청 결과 화면이다
    EVAL_APPROVED("차량 평가가 승인되었습니다. 경매글을 등록해 주세요.", "/sell/result"),
    EVAL_REJECTED("차량 평가가 반려되었습니다. 사유를 확인해 주세요.", "/sell/result"),

    // 낙찰과 동시에 거래가 만들어지므로 경매방이 아니라 거래로 보낸다. 경매방은 결과 확인 5분이
    // 지나면 볼 것이 없지만, 낙찰자가 실제로 해야 할 일은 거래 화면에 있다
    AUCTION_WON("낙찰되었습니다. 거래를 진행해 주세요.", "/deals/%d"),

    // 유찰이거나 낙찰되지 못한 참여자에게 간다. 거래가 없으므로 경매방으로 보낸다
    AUCTION_ENDED("경매가 종료되었습니다.", "/auctions/%d"),

    DEAL_STATUS_CHANGED("거래가 다음 단계로 넘어갔습니다.", "/deals/%d");

    private static final String REFERENCE = "%d";

    private final String defaultMessage;
    private final String linkFormat;
    private final boolean referenced;

    NotificationType(String defaultMessage, String linkFormat) {
        this.defaultMessage = defaultMessage;
        this.linkFormat = linkFormat;
        // 자리표시자가 있는지 상수를 만들 때 한 번만 본다, 링크를 만들 때마다 문자열을 훑지 않는다
        this.referenced = linkFormat.contains(REFERENCE);
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * 알림을 눌렀을 때 갈 곳
     *
     * @throws IllegalStateException 참조가 필요한 종류인데 참조가 비어 있을 때. 사용자가 고칠 수
     *                               있는 문제가 아니라 발행한 쪽이 잘못 저장한 것이라 BusinessException 이 아니다
     */
    public String linkTo(Long referenceId) {
        if (!referenced) {
            return linkFormat;
        }

        if (referenceId == null) {
            throw new IllegalStateException("참조가 필요한 알림에 참조가 없습니다: " + name());
        }

        return linkFormat.formatted(referenceId);
    }
}