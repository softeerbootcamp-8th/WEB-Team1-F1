package com.softeer.race.auction.application;

import com.softeer.race.auction.application.dto.AuctionUpdateInfo;
import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T06:30:00Z"), KST);

    private static final Long VEHICLE_ID = 1000L;
    private static final long START_PRICE = 10_000_000L;

    private static final LocalDateTime VALID_START_AT = LocalDateTime.of(2026, 7, 27, 16, 30);

    private static final long AUCTION_ID = 200L;
    private static final long SELLER_ID = 500L;

    @Mock
    private AuctionPostRepository auctionPostRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private VehicleRepository vehicleRepository;

    private AuctionService service;

    @BeforeEach
    void before() {
        service = new AuctionService(auctionPostRepository, auctionRepository, vehicleRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("경매글을 등록하면 경매글과 경매과 함께 저장된다.")
    void create_성공() {
        Vehicle vehicle = diagnosedVehicle(SELLER_ID);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(auctionPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(auctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.create(SELLER_ID, VEHICLE_ID, START_PRICE, VALID_START_AT);
        then(auctionPostRepository).should().save(any(AuctionPost.class));
        then(auctionRepository).should().save(any(Auction.class));
    }

    @Test
    @DisplayName("차량 소유자가 아니면 경매글을 등록할 수 없다.")
    void create_소유자_아님_거부() {
        long strangerId = SELLER_ID + 1;
        Vehicle vehicle = vehicle(SELLER_ID);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.create(strangerId, VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.NOT_VEHICLE_OWNER);

        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("평가사 승인 전인 차량은 경매글을 등록할 수 없다.")
    void create_미승인_차량_거부() {
        Vehicle vehicle = vehicle(SELLER_ID);
        given(vehicle.isDiagnosed()).willReturn(false);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.create(SELLER_ID, VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.VEHICLE_NOT_APPROVED);

        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("미승인 차량은 중복 경매 검사에 도달하기 전에 거부된다.")
    void create_승인검사가_중복검사보다_먼저() {
        Vehicle vehicle = vehicle(SELLER_ID);
        given(vehicle.isDiagnosed()).willReturn(false);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.create(SELLER_ID, VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class);

        then(auctionRepository).should(never()).existsActiveByVehicleId(any(), any());
    }

    @Test
    @DisplayName("이미 진행 중인 경매가 있는 차량은 다시 등록할 수 없다.")
    void create_중복_경매_거부() {
        Vehicle vehicle = diagnosedVehicle(SELLER_ID);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(auctionRepository.existsActiveByVehicleId(any(), any())).willReturn(true);

        assertThatThrownBy(() -> service.create(SELLER_ID, VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class);

        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("유찰된 경매는 재등록 판단 대상 상태에서 제외된다.")
    void create_유찰은_활성상태_아님() {
        Vehicle vehicle = diagnosedVehicle(SELLER_ID);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(auctionRepository.existsActiveByVehicleId(any(), any())).willReturn(false);
        given(auctionPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(auctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.create(SELLER_ID, VEHICLE_ID, START_PRICE, VALID_START_AT);

        ArgumentCaptor<Collection<AuctionStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        then(auctionRepository).should().existsActiveByVehicleId(eq(VEHICLE_ID), statusesCaptor.capture());

        assertThat(statusesCaptor.getValue())
                .doesNotContain(AuctionStatus.FAILED)
                .contains(AuctionStatus.SCHEDULED, AuctionStatus.IN_PROGRESS, AuctionStatus.ENDED);
    }

    @Test
    @DisplayName("존재하지 않는 차량이면 경매글을 등록할 수 없다.")
    void create_차량_없음_거부() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(SELLER_ID, VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class);

        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).should(never()).save(any());
    }


    // ================= 수정 (방 개설 전) =================

    @Test
    @DisplayName("판매자 본인이 방 개설 전 경매를 수정하면 값이 갱신된다.")
    void update_성공() {
        Auction auction = auctionOf(VALID_START_AT); // roomOpenAt = 16:00, FIXED_CLOCK now = 15:30 → 편집 가능
        given(auctionRepository.isSeller(AUCTION_ID, SELLER_ID)).willReturn(true);
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.of(auction));

        LocalDateTime newStartAt = LocalDateTime.of(2026, 7, 27, 18, 0);
        AuctionUpdateInfo info = service.update(SELLER_ID, AUCTION_ID, 20_000_000L, newStartAt);

        assertThat(info.startPrice()).isEqualTo(20_000_000L);
        assertThat(info.startAt()).isEqualTo(newStartAt);
        assertThat(info.roomOpenAt()).isEqualTo(newStartAt.minusMinutes(30));
        assertThat(info.endAt()).isEqualTo(newStartAt.plusMinutes(20));
    }

    @Test
    @DisplayName("판매자 본인이 아니면 수정할 수 없다.")
    void update_인가_실패() {
        // 존재하지 않는 경매까지 403으로 답하지 않으려면 조회가 인가보다 먼저다,
        // 그래서 이 테스트도 경매가 실제로 있는 상태에서 인가만 실패하는 경우를 검증한다
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.of(auctionOf(VALID_START_AT)));
        given(auctionRepository.isSeller(AUCTION_ID, SELLER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.update(SELLER_ID, AUCTION_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.NOT_AUCTION_SELLER);
    }

    @Test
    @DisplayName("존재하지 않는 경매는 수정할 수 없다.")
    void update_경매_없음_거부() {
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(SELLER_ID, AUCTION_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.AUCTION_NOT_FOUND);

        // 존재하지 않으면 403이 아니라 404여야 한다, 그래서 조회 실패 시 인가 체크에 도달하면 안 된다
        then(auctionRepository).should(never()).isSeller(anyLong(), anyLong());
    }

    @Test
    @DisplayName("경매방이 열린 뒤에는 수정할 수 없다.")
    void update_방개설후_거부() {
        // startAt 16:00 → roomOpenAt 15:30, FIXED_CLOCK now도 15:30이라 정각에 이미 열린 상태
        Auction auction = auctionOf(LocalDateTime.of(2026, 7, 27, 16, 0));
        given(auctionRepository.isSeller(AUCTION_ID, SELLER_ID)).willReturn(true);
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.update(SELLER_ID, AUCTION_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.AUCTION_ROOM_ALREADY_OPEN);
    }

    // ================= 삭제 (경매 종료 후) =================

    @Test
    @DisplayName("판매자 본인이 종료된 경매를 삭제하면 경매글에 삭제 시각이 채워진다.")
    void delete_성공() {
        Auction auction = endedAuction();
        given(auctionRepository.isSeller(AUCTION_ID, SELLER_ID)).willReturn(true);
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.of(auction));

        service.delete(SELLER_ID, AUCTION_ID);

        assertThat(auction.getPost().getDeletedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
    }

    @Test
    @DisplayName("판매자 본인이 아니면 삭제할 수 없다.")
    void delete_인가_실패() {
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.of(endedAuction()));
        given(auctionRepository.isSeller(AUCTION_ID, SELLER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.delete(SELLER_ID, AUCTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.NOT_AUCTION_SELLER);
    }

    @Test
    @DisplayName("존재하지 않는 경매는 삭제할 수 없다.")
    void delete_경매_없음_거부() {
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(SELLER_ID, AUCTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.AUCTION_NOT_FOUND);

        then(auctionRepository).should(never()).isSeller(anyLong(), anyLong());
    }

    @Test
    @DisplayName("종료되지 않은 경매는 삭제할 수 없다.")
    void delete_종료전_거부() {
        Auction auction = auctionOf(VALID_START_AT); // SCHEDULED

        given(auctionRepository.isSeller(AUCTION_ID, SELLER_ID)).willReturn(true);
        given(auctionRepository.findWithPostById(AUCTION_ID)).willReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.delete(SELLER_ID, AUCTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.AUCTION_NOT_ENDED);

        assertThat(auction.getPost().getDeletedAt()).isNull();
    }

    private Vehicle vehicle() {
        return mock(Vehicle.class);
    }

    private Vehicle vehicle(long sellerId) {
        Vehicle vehicle = mock(Vehicle.class);
        User seller = mock(User.class);
        given(seller.getId()).willReturn(sellerId);
        given(vehicle.getSeller()).willReturn(seller);
        return vehicle;
    }

    private Vehicle diagnosedVehicle(long sellerId) {
        Vehicle vehicle = vehicle(sellerId);
        given(vehicle.isDiagnosed()).willReturn(true);
        return vehicle;
    }

    // AuctionUpdateInfo.from이 post.getVehicle().getId()를 읽으므로 create 테스트의 vehicle()과 달리
    // AuctionPost에 null이 아닌 차량을 반드시 붙여야 한다
    private Auction auctionOf(LocalDateTime startAt) {
        AuctionPost post = AuctionPost.create(vehicle(), startAt.minusHours(2));
        return Auction.schedule(post, START_PRICE, startAt);
    }

    // 상태 전이를 직접 세팅하지 않고 도메인 전이를 그대로 태운다, 입찰을 넣지 않아 유찰(FAILED)로 끝난다
    private Auction endedAuction() {
        LocalDateTime startAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        Auction auction = auctionOf(startAt);
        auction.start(startAt);
        auction.close(auction.getCurrentEndTime());

        return auction;
    }

}