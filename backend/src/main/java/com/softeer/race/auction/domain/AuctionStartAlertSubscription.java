package com.softeer.race.auction.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경매 시작 알림 신청 한 건
 * <p>
 * 행의 존재가 곧 "아직 보내지 않음"이다. 발송에 성공하면 지우므로 이 표 전체가 발송 대기 목록이 되고,
 * 발송 여부를 따로 표시하는 값이 필요 없다. 발송 뒤 신청 이력을 남겨야 하는 요구가 생기면 그때 다시 본다.
 * <p>
 * 수정 메서드가 없다. 신청은 취소도 변경도 없어 한 번 저장되면 지워질 때까지 그대로다.
 */
@Getter
@Entity
// 신청 여부 조회가 이 유니크 인덱스를 그대로 탄다, 조회용 인덱스를 따로 두지 않는다.
// 선두 컬럼이 auction_id 라 발송 후보 조회가 경매로 조인하는 경로도 같은 인덱스를 쓴다.
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_auction_start_alert_subscription_auction_user",
        columnNames = {"auction_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionStartAlertSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 신청 가능 여부는 호출자가 경매를 잠근 뒤에 판정한다.
     * 여기서 다시 보면 잠금 없이 통과한 경로가 정상처럼 보인다.
     */
    public static AuctionStartAlertSubscription of(Auction auction, User user) {
        AuctionStartAlertSubscription subscription = new AuctionStartAlertSubscription();
        subscription.auction = auction;
        subscription.user = user;

        return subscription;
    }
}