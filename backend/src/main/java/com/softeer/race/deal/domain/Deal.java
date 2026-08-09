package com.softeer.race.deal.domain;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.deal.exception.DealErrorCode;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 판매자와 구매자가 같은 거래를 동시에 옮기려 할 때 한쪽만 통과시킨다.
    // 전이는 단계당 한 번뿐이라 미리 잠그지 않는다, 기한 스케줄러가 사용자를 기다리게 된다
    @Version
    private long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false, unique = true)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStatus status;

    // 낙찰 순간에 고정된다, 경매의 현재가는 입찰이 갱신하는 값이라 겸할 수 없다
    @Column(nullable = false)
    private long finalPrice;

    // 기한의 기준이다. updatedAt 은 탁송지 수정으로도 갱신돼 기한이 뒤로 밀린다
    @Column(nullable = false)
    private LocalDateTime statusChangedAt;

    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;

    private LocalDate transportDate;

    private String transportLocation;

    private String deliveryLocation;

    /**
     * 낙찰로 거래를 연다
     *
     * @param finalPrice 낙찰가, 낙찰자가 있는데 비어 있으면 데이터가 깨진 것이라 사용자가 고칠 수 없다
     */
    public static Deal start(Auction auction, User seller, User buyer, Long finalPrice, LocalDateTime now) {
        if (finalPrice == null || finalPrice <= 0) {
            throw new IllegalStateException(
                    "낙찰가 없이 거래를 만들 수 없다, 경매 %d 금액 %s".formatted(auction.getId(), finalPrice));
        }

        Deal deal = new Deal();
        deal.auction = auction;
        deal.seller = seller;
        deal.buyer = buyer;
        deal.finalPrice = finalPrice;
        deal.status = DealStatus.DEPOSIT_PENDING;
        deal.statusChangedAt = now;

        return deal;
    }

    /**
     * 다음 단계로 넘긴다
     * <p>
     * 어긋난 순서는 화면을 다시 읽으면 해소되는 실패라 BusinessException 이다.
     * 같은 요청이 두 번 와도 두 번째는 여기서 걸린다.
     */
    public void transitionTo(DealStatus target, LocalDateTime now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(DealErrorCode.INVALID_TRANSITION);
        }

        status = target;
        statusChangedAt = now;
    }

    /**
     * 거래를 취소한다, 사유가 귀책과 보증금 향방을 결정한다
     */
    public void cancel(CancellationReason reason, LocalDateTime now) {
        if (!status.isCancellable()) {
            throw new BusinessException(DealErrorCode.NOT_CANCELLABLE);
        }

        status = DealStatus.CANCELLED;
        cancellationReason = reason;
        statusChangedAt = now;
    }
}
