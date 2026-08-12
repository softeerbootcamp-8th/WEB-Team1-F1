package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStartAlertSubscription;
import com.softeer.race.auction.domain.AuctionStartAlertSubscriptionRepository;
import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 경매 시작 알림 신청
 * <p>
 * 등록·수정·삭제를 담은 AuctionService 와 나눠 둔다. 경매 자체를 바꾸는 일이 아니라 회원이 관심을
 * 남기는 일이고, 발송 쪽과 짝이 되는 코드라 함께 읽혀야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionStartAlertService {

    private final AuctionRepository auctionRepository;
    private final AuctionStartAlertSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 시작 알림을 신청한다
     * <p>
     * <b>경매를 잠그는 이유.</b> 시작 전인지 확인한 뒤 저장하기까지 사이에 시작 전이가 끼어들면
     * 이미 시작된 경매에 신청이 들어간다. 유니크 제약은 같은 회원의 중복만 막고 이 시간 경합은
     * 막지 못한다. 잠금이 순서를 정해 준다 — 신청이 먼저면 저장 후 시작, 시작이 먼저면 신청 거절.
     * <p>
     * 잠금 전에 값싼 사전 검사를 두지 않는다. 신청은 시작 전 구간에만 되어 입찰과 시간대가 겹치지
     * 않고, 그래서 걷어낼 잠금 대기열이 없다. 검증만 두 곳으로 늘어난다.
     * <p>
     * 알림을 여기서 발행하지 않는다. 잠금을 쥔 채 신청자 수만큼 저장하면 시작 직후 몰리는 입찰이
     * 그만큼 기다린다. 발송은 경매 진행 주기의 마지막 단계가 맡는다.
     *
     * @return 이번 요청으로 새로 신청됐으면 true, 이미 신청돼 있었으면 false
     */
    @Transactional
    public boolean subscribe(long auctionId, long userId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));

        // 잠금을 얻은 뒤에 찍는다. 위로 올리면 잠금 대기 시간만큼 시각이 낡아,
        // 그 사이 시작된 경매에 신청이 성립한다. BidService 가 acceptedAt 을 잠금 아래에서 찍는 것과 같다.
        LocalDateTime now = LocalDateTime.now(clock);

        if (!auction.isStartAlertOpenAt(now)) {
            throw new BusinessException(AuctionErrorCode.START_ALERT_NOT_OPEN);
        }

        // 잠금 안이라 확인과 저장 사이에 남의 신청이 끼어들 수 없다.
        // 같은 회원의 동시 요청은 두 번째가 잠금을 기다린 뒤 여기서 걸러진다.
        if (subscriptionRepository.existsByAuctionIdAndUserId(auctionId, userId)) {
            return false;
        }

        // 회원 엔티티가 아니라 프록시 참조를 넣는다, FK 컬럼만 쓰이므로 회원 조회가 나가지 않는다
        subscriptionRepository.save(
                AuctionStartAlertSubscription.of(auction, userRepository.getReferenceById(userId)));

        return true;
    }
    /**
     * 이 회원이 이 경매의 시작 알림을 신청했는지
     * <p>
     * <b>경매 존재 여부를 확인하지 않는다.</b> 이 조회가 답하는 것은 경매가 있는지가 아니라 부르는
     * 회원의 신청 여부다. 경매가 없으면 이 화면을 띄운 본 요청(방 조회·목록 미리보기)이 이미 404 로
     * 막았고, 여기서 한 번 더 확인하면 방에 들어올 때마다 조회가 한 번 늘어난다.
     * <p>
     * 발송에 성공하면 신청이 지워지므로 시작 뒤에는 신청했던 회원에게도 거짓이 돌아간다.
     * 시작 뒤에는 신청 화면 자체가 없어 사용자에게 보이지 않는다.
     */
    public boolean isSubscribed(long auctionId, long userId) {
        return subscriptionRepository.existsByAuctionIdAndUserId(auctionId, userId);
    }
}