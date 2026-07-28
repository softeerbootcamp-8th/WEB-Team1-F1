package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private AuctionPostRepository auctionPostRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private VehicleImageRepository vehicleImageRepository;

    private AuctionService service;

    @BeforeEach
    void before() {
        service = new AuctionService(auctionPostRepository, auctionRepository,
                vehicleRepository, vehicleImageRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("경매글을 등록하면 경매글과 경매과 함께 저장된다.")
    void create_성공() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle()));
        given(vehicleImageRepository.findFirstByVehicleOrderBySortOrderAsc(any()))
                .willReturn(Optional.empty());
        given(auctionPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(auctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.create(VEHICLE_ID, START_PRICE, VALID_START_AT);
        then(auctionPostRepository).should().save(any(AuctionPost.class));
        then(auctionRepository).should().save(any(Auction.class));
    }

    @Test
    @DisplayName("차량 이미지 중 sortOrder가 가장 앞선 이미지가 썸네일이 된다.")
    void create_썸네일_자동_선택() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle()));
        VehicleImage image = vehicleImage("https://cdn/first.jpg");
        given(vehicleImageRepository.findFirstByVehicleOrderBySortOrderAsc(any()))
                .willReturn(Optional.of(image));
        given(auctionPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(auctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.create(VEHICLE_ID, START_PRICE, VALID_START_AT);

        ArgumentCaptor<AuctionPost> captor = ArgumentCaptor.forClass(AuctionPost.class);
        then(auctionPostRepository).should().save(captor.capture());

        assertThat(captor.getValue().getThumbnailUrl()).isEqualTo("https://cdn/first.jpg");
    }

    @Test
    @DisplayName("이미 진행 중인 경매가 있는 차량은 다시 등록할 수 없다.")
    void create_중복_경매_거부() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle()));
        given(auctionRepository.existsActiveByVehicleId(any(), any())).willReturn(true);

        assertThatThrownBy(() -> service.create(VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class);

        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 차량이면 경매글을 등록할 수 없다.")
    void create_차량_없음_거부() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(VEHICLE_ID, START_PRICE, VALID_START_AT))
                .isInstanceOf(BusinessException.class);

        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).should(never()).save(any());
    }


    private Vehicle vehicle() {
        return mock(Vehicle.class);
    }

    private VehicleImage vehicleImage(String url) {
        VehicleImage image = mock(VehicleImage.class);
        given(image.getImageUrl()).willReturn(url);
        return image;
    }

}