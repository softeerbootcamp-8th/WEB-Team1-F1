package com.softeer.race.sell.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.sell.application.dto.command.SellApplicationCommand;
import com.softeer.race.sell.application.dto.info.SellApplicationInfo;
import com.softeer.race.sell.exception.SellErrorCode;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleRepository;
import com.softeer.race.vehicle.domain.VehicleSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 판매 신청. 회원이 번호판만 보내면 서버가 제원을 재조회해 차량 · 경매글 · 경매를 한 트랜잭션으로 만든다.
 * <p>
 * 평가 요청(Evaluation)은 만들지 않는다. 데모에서는 판매 신청이 곧바로 발행된 경매글과 예약된 경매가
 * 되어 경매 목록에 노출되는 것이 목표다.
 * <p>
 * AuctionService.create를 재사용하지 않고 경매글과 경매를 직접 조립한다. 이유는 {@link #apply}의
 * 시각 처리 주석 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellService {

    private static final int MAIN_IMAGE_SORT_ORDER = 1;

    // Auction.MIN_LEAD_TIME_HOURS와 같은 값이어야 한다(그쪽이 private이라 여기 상수를 따로 둔다).
    // 어긋나면 판매 신청이 전부 INVALID_START_AT 400이 되고 통합 테스트 시나리오 1이 그것을 잡는다
    private static final Duration START_DELAY = Duration.ofHours(1);

    private final VehicleLookup vehicleLookup;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final AuctionPostRepository auctionPostRepository;
    private final AuctionRepository auctionRepository;
    private final Clock clock;

    /**
     * 번호판으로 제원을 조회해 차량을 등록하고, 발행된 경매글과 예약된 경매를 함께 만든다.
     */
    @Transactional
    public SellApplicationInfo apply(SellApplicationCommand command) {
        // 제원은 클라이언트가 아니라 서버가 조회한다. 클라이언트 값을 믿으면 연식·주행거리를 위조할 수 있다
        VehicleSpec spec = vehicleLookup.findByPlateNumber(command.plateNumber())
                .orElseThrow(() -> new BusinessException(SellErrorCode.VEHICLE_NOT_FOUND));

        // 소유자명(spec.ownerName())과 판매자 실명이 일치하는지는 검사하지 않는다. 명세에서 빠진 요구다.
        // spec에 ownerName을 남겨 두는 것은 제원 조회 API가 소유자 확인에 쓸 계약이기 때문이다

        // getReferenceById가 아니라 findById를 쓴다. 존재 확인을 flush로 미루면 계정이 사라진 경우
        // vehicle.seller_id FK 위반 → DataIntegrityViolationException → 최후방 핸들러의 500이 된다.
        // 아끼는 것은 PK 단건 SELECT 하나뿐이고, 판매 신청은 저빈도 쓰기다
        User seller = userRepository.findById(command.sellerId())
                .orElseThrow(() -> new BusinessException(SellErrorCode.SELLER_NOT_FOUND));

        // 시각은 반드시 한 번만 읽는다. publishedAt과 startAt이 서로 다른 시각에서 계산되면
        // Auction.schedule의 최소 리드타임 검증(startAt >= publishedAt + 1h)이 항상 실패한다.
        // 이 실패는 고정 Clock 테스트에서는 두 값이 같은 틱에 떨어져 재현되지 않는다
        LocalDateTime now = LocalDateTime.now(clock);
        // 정확히 경계에 걸치지 않게 다음 분으로 올린다. 데모 시작 시각도 초 단위가 아니라 깔끔해진다
        LocalDateTime startAt = now.plus(START_DELAY).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);

        // 번호판 중복을 검사하지 않는다. 반복 신청하면 매번 새 차량이 생기므로
        // AuctionPost.vehicle의 unique 제약과도 충돌하지 않는다
        Vehicle vehicle = vehicleRepository.save(Vehicle.create(seller, spec));

        if (spec.mainImageUrl() != null) {
            vehicleImageRepository.save(
                    VehicleImage.create(vehicle, spec.mainImageUrl(), MAIN_IMAGE_SORT_ORDER));
        }

        // 방금 저장한 이미지를 findFirstByVehicleOrderBySortOrderAsc로 되읽을 이유가 없다
        AuctionPost post = auctionPostRepository.save(
                AuctionPost.create(vehicle, spec.mainImageUrl(), now));
        Auction auction = auctionRepository.save(
                Auction.schedule(post, spec.basePrice(), startAt));

        return SellApplicationInfo.from(auction);
    }
}
