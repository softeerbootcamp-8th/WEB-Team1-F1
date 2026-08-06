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
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
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
    private final VehicleImageRepository vehicleImageRepository;
    private final Clock clock;

    // 재등록을 막아야 하는 상태.
    private static final Set<AuctionStatus> ACTIVE_STATUSES =
            Set.of(AuctionStatus.SCHEDULED, AuctionStatus.IN_PROGRESS, AuctionStatus.ENDED);

    /**
     * 경매글과 경매를 한 트랜잭션으로 함께 생성한다
     */
    @Transactional
    public AuctionCreateInfo create(long sellerId, Long vehicleId, long startPrice, LocalDateTime startAt) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.VEHICLE_NOT_FOUND));

        if (!vehicle.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_VEHICLE_OWNER);
        }

        if (auctionRepository.existsActiveByVehicleId(vehicleId, ACTIVE_STATUSES)) {
            throw new BusinessException(AuctionErrorCode.AUCTION_ALREADY_EXISTS);
        }

        // 차량 이미지 중 첫 번째를 대표 이미지로 쓰고, 이미지가 없으면 썸네일 없이 등록한다
        String thumbnailUrl = vehicleImageRepository.findFirstByVehicleOrderBySortOrderAsc(vehicle)
                .map(VehicleImage::getImageUrl)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now(clock);
        AuctionPost post = auctionPostRepository.save(
                AuctionPost.create(vehicle, thumbnailUrl, now));
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
