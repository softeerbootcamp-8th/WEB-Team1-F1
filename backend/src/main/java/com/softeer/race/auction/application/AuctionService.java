package com.softeer.race.auction.application;

import com.softeer.race.auction.application.dto.AuctionCreateInfo;
import com.softeer.race.auction.application.dto.AuctionUpdateInfo;
import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionPostRepository auctionPostRepository;
    private final AuctionRepository auctionRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    // 재등록을 막아야 하는 상태.
    private static final Set<AuctionStatus> ACTIVE_STATUSES =
            Set.of(AuctionStatus.SCHEDULED, AuctionStatus.IN_PROGRESS, AuctionStatus.ENDED);

    /**
     * 경매글과 경매를 한 트랜잭션으로 함께 생성한다
     */
    @Transactional
    public AuctionCreateInfo create(long sellerId, Long vehicleId, long startPrice, LocalDateTime startAt) {
        // 평가 결과 변경과 같은 차량 행을 잠근다. 둘 중 먼저 잠근 요청이 끝난 뒤 다른 요청이
        // 최신 상태를 검증하므로, 경매가 만들어진 뒤 평가 결과가 바뀌는 틈이 생기지 않는다.
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.VEHICLE_NOT_FOUND));

        if (!vehicle.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_VEHICLE_OWNER);
        }

        if (!vehicle.isDiagnosed()) {
            throw new BusinessException(AuctionErrorCode.VEHICLE_NOT_APPROVED);
        }

        if (auctionRepository.existsActiveByVehicleId(vehicleId, ACTIVE_STATUSES)) {
            throw new BusinessException(AuctionErrorCode.AUCTION_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        AuctionPost post = auctionPostRepository.save(AuctionPost.create(vehicle, now));
        Auction auction = auctionRepository.save(
                Auction.schedule(post, startPrice, startAt));

        return AuctionCreateInfo.from(auction);
    }

    @Transactional
    public AuctionUpdateInfo update(long sellerId, long auctionId, long startPrice, LocalDateTime startAt) {

        Auction auction = auctionRepository.findWithPostById(auctionId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));


        if (!auctionRepository.isSeller(auctionId, sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_AUCTION_SELLER);
        }

        auction.updateBeforeRoomOpens(startPrice, startAt, LocalDateTime.now(clock));

        return AuctionUpdateInfo.from(auction);
    }

    @Transactional
    public void delete(long sellerId, long auctionId) {

        Auction auction = auctionRepository.findWithPostById(auctionId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));

        if (!auctionRepository.isSeller(auctionId, sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_AUCTION_SELLER);
        }

        if (auction.getStatus() != AuctionStatus.ENDED && auction.getStatus() != AuctionStatus.FAILED) {
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_ENDED);
        }

        auction.getPost().delete(LocalDateTime.now(clock));
    }
}
