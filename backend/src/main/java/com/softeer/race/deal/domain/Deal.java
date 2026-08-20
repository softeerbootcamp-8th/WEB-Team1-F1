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
    // 전이는 단계당 한 번뿐이라 미리 잠그지 않는다, 락을 쥔 채 기다릴 상대가 없다
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

    // 기한을 재게 될 때의 기준 자리다. updatedAt 은 탁송지 수정으로도 갱신돼 기한이 뒤로 밀린다
    @Column(nullable = false)
    private LocalDateTime statusChangedAt;

    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;

    // 판매자가 낸 서류. 파일은 브라우저가 S3 로 직접 올리고 여기에는 조회 주소만 남는다.
    // 서버로 20MB 를 받아 넘기면 톰캣 스레드가 업로드 시간 내내 묶인다
    @Column(length = 512)
    private String documentUrl;

    // 판매자가 차를 넘기는 시각과 자리
    private LocalDateTime transportAt;

    private String transportLocation;

    // 구매자가 차를 받는 시각과 자리, 탁송보다 뒤여야 한다
    private LocalDateTime deliveryAt;

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
        deal.status = DealStatus.BUYER_CONFIRM_PENDING;
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
     * 구매자가 구매를 확정한다, 여기서부터 판매자 차례다
     */
    public void confirmPurchase(LocalDateTime now) {
        transitionTo(DealStatus.SELLER_SUBMIT_PENDING, now);
    }

    /**
     * 판매자가 명의이전 서류와 탁송 일정을 낸다
     * <p>
     * 단계 검사를 값 검증보다 먼저 한다. 순서를 뒤집으면 남의 차례에 보낸 요청이 "날짜가 과거"라고
     * 답해서, 무엇이 틀렸는지 알 수 없는 응답이 나간다. 검사에서 걸리면 트랜잭션이 롤백되므로
     * 먼저 바뀐 상태는 저장되지 않는다.
     */
    public void submitTransport(String documentUrl, LocalDateTime transportAt,
                                String transportLocation, LocalDateTime now) {
        transitionTo(DealStatus.BUYER_SCHEDULE_PENDING, now);

        if (!transportAt.isAfter(now)) {
            throw new BusinessException(DealErrorCode.PAST_TRANSPORT_SCHEDULE);
        }

        this.documentUrl = documentUrl;
        this.transportAt = transportAt;
        this.transportLocation = transportLocation;
    }

    /**
     * 구매자가 탁송 일정에 동의하고 차량 인수 일정을 잡는다, 동의는 별도 값이 아니라 이 호출 자체다
     * <p>
     * 인수가 탁송보다 앞서면 차가 출발하기 전에 받는 약속이 된다. 현재 시각이 아니라 탁송 시각과
     * 비교하는 이유다 — 지금보다 미래여도 탁송보다 앞설 수 있다.
     */
    public void confirmDelivery(LocalDateTime deliveryAt, String deliveryLocation, LocalDateTime now) {
        transitionTo(DealStatus.CONFIRMED, now);

        if (!deliveryAt.isAfter(transportAt)) {
            throw new BusinessException(DealErrorCode.DELIVERY_BEFORE_TRANSPORT);
        }

        this.deliveryAt = deliveryAt;
        this.deliveryLocation = deliveryLocation;
    }

    /**
     * 이 사용자가 거래에서 어느 쪽인가
     * <p>
     * 남의 거래는 조회와 같은 이유로 없는 것과 같게 답한다 — 권한 없음으로 갈리면 그 번호의
     * 거래가 존재한다는 사실이 새어 나간다.
     * <p>
     * 판정이 도메인에 있는 이유는 당사자인지 묻는 곳이 진행 말고도 생겼기 때문이다. 서비스마다
     * 들고 있으면 "남의 거래는 404" 같은 규칙이 한쪽에서만 빠질 수 있다.
     */
    public DealSide sideOf(long userId) {
        if (seller.getId() == userId) {
            return DealSide.SELLER;
        }
        if (buyer.getId() == userId) {
            return DealSide.BUYER;
        }

        throw new BusinessException(DealErrorCode.NOT_FOUND);
    }

    /**
     * 거래를 그만둔다, 사유가 어느 쪽 귀책인지까지 정한다
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
