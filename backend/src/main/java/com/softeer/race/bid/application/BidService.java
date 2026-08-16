package com.softeer.race.bid.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.bid.application.dto.BidPlaceInfo;
import com.softeer.race.bid.application.dto.BidPlaced;
import com.softeer.race.bid.domain.AuctionBidSnapshot;
import com.softeer.race.bid.domain.Bid;
import com.softeer.race.bid.domain.BidAccepted;
import com.softeer.race.bid.domain.BidIncrementTable;
import com.softeer.race.bid.domain.BidPreCheck;
import com.softeer.race.bid.domain.BidPreCheckRepository;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.common.domain.MaskedName;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.notification.domain.NotificationContent;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.softeer.race.bid.exception.BidErrorCode.AUCTION_NOT_FOUND;
import static com.softeer.race.bid.exception.BidErrorCode.AUCTION_NOT_LIVE;
import static com.softeer.race.bid.exception.BidErrorCode.BIDDER_NOT_FOUND;
import static com.softeer.race.bid.exception.BidErrorCode.EVALUATOR_CANNOT_BID;
import static com.softeer.race.bid.exception.BidErrorCode.SELF_OUTBID;

/**
 * 입찰 접수
 */
@Service
@RequiredArgsConstructor
public class BidService {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final BidPreCheckRepository bidPreCheckRepository;
    private final BidIncrementService bidIncrementService;
    private final NotificationPublisher notificationPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 입찰을 접수한다, 성립하면 경매의 현재가와 마감이 함께 갱신된다.
     */
    @Transactional
    public BidPlaced place(long auctionId, long bidderId, long amount) {
        // 락 없이 끝낼 수 있는 일을 먼저 한다.
        // 마감 직전에는 한 경매의 입찰이 줄을 서므로, 락 안에서 보낸 시간이 대기열 전체에 곱해진다.
        BidIncrementTable table = bidIncrementService.loadTable();

        // 결과가 비면 회원이 없다는 뜻이다, 경매가 없는 경우는 판정용 값들이 null 로 온다.
        BidPreCheck preCheck = bidPreCheckRepository.find(auctionId, bidderId)
                .orElseThrow(() -> new BusinessException(BIDDER_NOT_FOUND));

        if (!preCheck.hasAuction()) {
            throw new BusinessException(AUCTION_NOT_FOUND);
        }

        if (preCheck.isEvaluator()) {
            throw new BusinessException(EVALUATOR_CANNOT_BID);
        }

        // 확실히 떨어질 요청은 잠금 대기열에 세우지 않는다.
        preCheck.toSnapshot().rejectIfDoomed(table, bidderId, amount, LocalDateTime.now(clock));

        // 여기부터 이 경매에 대한 입찰이 한 번에 하나씩 처리된다.
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));

        // 이 줄을 잠금 위로 올리지 마라. 잠금 대기 시간만큼 시각이 낡아 이미 마감된 경매에 입찰이 성립한다.
        // 여기서 찍은 값은 요청 도착 시각이 아니라 이 입찰이 순서를 배정받은 시각이고,
        // 순서를 정하는 것도 잠금이므로 순서와 시각이 같은 기준을 쓰게 된다.
        LocalDateTime acceptedAt = LocalDateTime.now(clock);

        // 저장된 status가 아니라 서버 시각으로 판정한다.
        // 아래 acceptBid에 같은 값을 넘겨야 연장 판정이 다른 시각을 보지 않는다.
        if (!auction.isBiddableAt(acceptedAt)) {
            throw new BusinessException(AUCTION_NOT_LIVE);
        }

        // 잠긴 행에서 읽는다. 잠금 밖 조회는 트랜잭션 스냅샷에 묶여 낡고,
        // 그 사이 남이 입찰하면 이미 밀려난 사람을 오거절한다.
        if (auction.isTopBidder(bidderId)) {
            throw new BusinessException(SELF_OUTBID);
        }

        table.ruleFor(auction.getStartPrice(), auction.getCurrentPrice()).validate(amount);

        // acceptBid가 최고 입찰자를 덮어쓰기 전에 알림 받을 사람을 보관한다
        Long previousTopBidderId = auction.getTopBidder() == null
                ? null
                : auction.getTopBidder().getId();

        // 위 사전 질의가 회원 존재를 확인했으므로 프록시로 참조만 건다, 회원 조회가 추가로 나가지 않는다.
        User bidder = userRepository.getReferenceById(bidderId);

        Bid bid = bidRepository.save(Bid.place(auction, bidder, amount));
        auction.acceptBid(bidder, amount, acceptedAt);

        // 입찰과 알림을 원자적으로 남기려고 같은 트랜잭션에 둔다. 그 대가로 알림 INSERT와
        // 안 읽은 건수 COUNT가 경매 락 보유 시간에 포함된다. 락 대기가 관측되면 아웃박스로 다시 본다.
        if (previousTopBidderId != null) {
            notificationPublisher.publishContent(
                    previousTopBidderId,
                    NotificationContent.outbid(
                            preCheck.vehicleName().display(),
                            MaskedName.mask(preCheck.bidderRealName()),
                            amount),
                    auctionId);
        }

        // 커밋 뒤에 처리된다, 방송이 잠금 안에서 돌면 그 시간이 대기열 전체에 곱해진다
        eventPublisher.publishEvent(new BidAccepted(auctionId));

        // acceptBid 뒤에 읽어야 연장된 마감이 담긴다.
        // 게이트용 사본은 값이 확정된 여기서 만든다 - 전부 손에 있는 값이라 쿼리가 늘지 않는다.
        AuctionBidSnapshot committed = new AuctionBidSnapshot(
                preCheck.sellerId(), preCheck.startPrice(), amount,
                preCheck.startTime(), auction.getCurrentEndTime());

        return new BidPlaced(
                new BidPlaceInfo(bid.getId(), amount, auction.getCurrentEndTime(), acceptedAt),
                committed);
    }
}
