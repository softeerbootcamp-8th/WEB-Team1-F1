package com.softeer.race.notification.domain;

/**
 * 알림 종류
 * <p>
 * 문구와 이동할 곳을 종류가 함께 들고 있다. 알림을 일으키는 쪽마다 문구를 조립하면 같은 종류가
 * 자리마다 다르게 보이고, 종류가 늘 때 이동할 곳 정하기를 빠뜨릴 수 있다. 여기에 상수를 추가하면
 * 둘 다 강제로 따라온다.
 */
public enum NotificationType {
    // 평가사 전원에게 가는 알림. 목적지가 배정 대기 목록이고 referenceId 는 링크에 쓰이지 않는다
    EVAL_REQUESTED("새 방문 진단 신청이 접수되었습니다.", "/evaluations/assignable"),

    // 승인 알림은 판매자가 평가 결과를 확인하고 등록 여부를 정할 수 있도록 신청 상세로 보낸다.
    // 참조가 차량이 아니라 신청 건인 이유는 #144 참고 — 신청 상세 하나로 시세까지 채워진다
    EVAL_APPROVED("차량 평가가 승인되었습니다. 경매글을 등록해 주세요.", "/mypage/evaluations/%d"),

    // 승인과 반려는 같은 평가 한 건의 결과라 같은 신청 상세로 보낸다. 반려 뒤에는 사유를 읽고
    // 다시 신청할지 정하고, 승인 뒤에는 평가 결과를 읽고 경매로 등록할지 정한다
    EVAL_REJECTED("차량 평가가 반려되었습니다. 사유를 확인해 주세요.", "/mypage/evaluations/%d"),
    // 시작 알림을 누른 사람이 할 일은 입찰이고, 그건 경매방에서만 한다. AUCTION_ENDED 와 같은 주소를
    // 쓰는 것은 같은 경매의 시작과 종료가 서로 다른 곳으로 가면 안 되기 때문이다.
    // 차량명은 NotificationContent 가 채운다 — 여러 건이 쌓이면 어느 차인지 문구로 가려야 한다
    AUCTION_STARTED("경매가 시작되었습니다.", "/auctions/%d"),

    // 낙찰과 동시에 거래가 만들어지므로 경매방이 아니라 거래로 보낸다. 경매방은 결과 확인 5분이
    // 지나면 볼 것이 없지만, 낙찰자가 실제로 해야 할 일은 거래 화면에 있다
    AUCTION_WON("낙찰되었습니다. 거래를 진행해 주세요.", "/mypage/deals/%d"),

    // 직전 최고 입찰자에게 간다. 차량·새 입찰자·금액은 NotificationContent가 완성한다.
    OUTBID("내 입찰보다 높은 입찰이 등록되었습니다.", "/auctions/%d"),

    // 낙찰되지 못한 참여자에게 간다. 거래가 없으므로 경매방으로 보낸다
    AUCTION_ENDED("경매가 종료되었습니다.", "/auctions/%d"),

    // 판매자는 유찰과 낙찰을 다른 알림으로 받는다. 결과가 반대이고 이어지는 행동도 달라서 —
    // 유찰이면 다시 등록해야 하고 낙찰이면 거래를 준비해야 한다 — 한 문구로 묶으면
    // 차가 팔렸는지를 알림만 보고 알 수 없다
    AUCTION_SOLD("등록하신 차량이 낙찰되었습니다.", "/auctions/%d"),
    AUCTION_FAILED("등록하신 경매가 입찰 없이 종료되었습니다.", "/auctions/%d"),

    // 단계별로 쪼갠다. 하나로 두면 "서류를 올려 주세요"와 "일정을 확인해 주세요"가 같은 문구로 떠서,
    // 눌러 보기 전에는 무엇을 해야 하는지 알 수 없다
    DEAL_SELLER_SUBMIT_REQUIRED("구매자가 구매를 확정했습니다. 서류와 탁송 일정을 등록해 주세요.", "/mypage/deals/%d"),
    DEAL_BUYER_SCHEDULE_REQUIRED("판매자가 탁송 일정을 등록했습니다. 인도 일정을 정해 주세요.", "/mypage/deals/%d"),
    DEAL_CONFIRMED("거래가 확정되었습니다. 인도 일정을 확인해 주세요.", "/mypage/deals/%d"),
    DEAL_CANCELLED("거래가 취소되었습니다.", "/mypage/deals/%d");

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
