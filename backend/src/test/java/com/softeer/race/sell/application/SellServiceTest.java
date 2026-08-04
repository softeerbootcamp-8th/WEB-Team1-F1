package com.softeer.race.sell.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.auctionpost.domain.PostStatus;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.sell.application.dto.command.SellApplicationCommand;
import com.softeer.race.sell.exception.SellErrorCode;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleRepository;
import com.softeer.race.vehicle.domain.VehicleSpec;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("판매 신청 서비스")
class SellServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 초·나노가 0이 아닌 시각을 일부러 쓴다, startAt이 분 단위로 올림되는지 함께 드러난다
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-30T11:31:17.123456Z"), KST);
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    private static final long SELLER_ID = 90L;
    private static final String PLATE_NUMBER = "12가3456";
    /** 신청자가 신고하는 값이다. 조회된 제원이 아니라 요청에서 온다 */
    private static final int MILEAGE = 45_000;
    private static final String IMAGE_URL = "https://cdn.race.dev/vehicles/grandeur-ig.jpg";

    /** 그 모델의 기준가. 개별 차량의 연식·주행거리가 빠진 신차급 값이다 */
    private static final long BASE_PRICE = 34_000_000L;

    /**
     * 위 기준가에 2021년식·4.5만km 감가를 반영한 값이다. QuotePolicy를 테스트에서 다시 호출해
     * 비교하면 정책이 무엇을 계산하든 통과하므로, 손으로 계산한 값을 적어 둔다.
     * 기준가 3400만 - 연식 5년(25%) 850만 - 주행 4.5만km(6.75%) 229.5만 = 2320.5만 → 만원 절사
     */
    private static final long ESTIMATED_PRICE = 23_200_000L;

    @Mock
    private VehicleLookup vehicleLookup;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private VehicleImageRepository vehicleImageRepository;
    @Mock
    private AuctionPostRepository auctionPostRepository;
    @Mock
    private AuctionRepository auctionRepository;

    private SellService service;

    @BeforeEach
    void before() {
        service = new SellService(vehicleLookup, userRepository, vehicleRepository,
                vehicleImageRepository, auctionPostRepository, auctionRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("번호판으로 조회한 제원으로 차량 · 경매글 · 경매를 함께 만든다")
    void apply성공() {
        // given
        givenLookup(spec(IMAGE_URL));
        givenSeller();
        givenSaveReturnsArgument();

        // when
        service.apply(command());

        // then 1 : 경매글은 발행 상태이고 발행 시각이 현재다
        AuctionPost post = capturedPost();
        assertThat(post.getPostStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(NOW);
        assertThat(post.getThumbnailUrl()).isEqualTo(IMAGE_URL);

        // then 2 : 차량은 조회된 제원 그대로다
        assertThat(post.getVehicle().getPlateNumber()).isEqualTo(PLATE_NUMBER);
        assertThat(post.getVehicle().getModelYear()).isEqualTo(2021);
        assertThat(post.getVehicle().getMileage()).isEqualTo(45_000);

        // then 3 : 차량의 예상 시세는 기준가가 아니라 감가를 반영한 값이다
        // 기준가가 그대로 남으면 목록 카드와 경매방 응답으로 신차급 가격이 흘러나간다
        assertThat(post.getVehicle().getEstimatedPrice()).isEqualTo(ESTIMATED_PRICE);

        // then 4 : 시작가도 같은 예상 시세다
        // 기준가를 시작가로 쓰면 시작가가 예상 시세보다 높아져 첫 입찰이 붙지 않는다
        Auction auction = capturedAuction();
        assertThat(auction.getStartPrice()).isEqualTo(ESTIMATED_PRICE);
        assertThat(auction.getStartPrice()).isLessThan(BASE_PRICE);

        // then 5 : 발행 시각으로부터 최소 리드타임(1시간)을 넘긴다
        // 시각을 두 번 읽는 구현이면 publishedAt이 startAt 계산 기준보다 뒤라 여기가 아니라
        // Auction.schedule에서 INVALID_START_AT으로 터진다
        assertThat(auction.getStartTime()).isAfter(post.getPublishedAt().plusHours(1));
        // 경계에 정확히 걸치지 않도록 다음 분으로 올린다
        assertThat(auction.getStartTime()).isEqualTo(NOW.plusHours(1).withSecond(0).withNano(0).plusMinutes(1));
        assertThat(auction.getRoomOpenAt()).isEqualTo(auction.getStartTime().minusMinutes(30));
        assertThat(auction.getCurrentEndTime()).isEqualTo(auction.getStartTime().plusMinutes(20));
    }

    @Test
    @DisplayName("대표 이미지가 있으면 sortOrder 1로 저장한다")
    void apply이미지_저장() {
        givenLookup(spec(IMAGE_URL));
        givenSeller();
        givenSaveReturnsArgument();

        service.apply(command());

        ArgumentCaptor<VehicleImage> captor = ArgumentCaptor.forClass(VehicleImage.class);
        then(vehicleImageRepository).should().save(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo(IMAGE_URL);
        assertThat(captor.getValue().getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("대표 이미지가 없으면 이미지를 저장하지 않고 썸네일도 비운다")
    void apply이미지_없음() {
        givenLookup(spec(null));
        givenSeller();
        givenSaveReturnsArgument();

        service.apply(command());

        then(vehicleImageRepository).shouldHaveNoInteractions();
        assertThat(capturedPost().getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("카탈로그에 없는 번호판이면 아무것도 저장하지 않고 404 코드로 거부한다")
    void apply미등록_번호판() {
        given(vehicleLookup.findByPlateNumber(PLATE_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(SellErrorCode.VEHICLE_NOT_FOUND);

        then(vehicleRepository).shouldHaveNoInteractions();
        then(auctionPostRepository).shouldHaveNoInteractions();
        then(auctionRepository).shouldHaveNoInteractions();
    }

    // getReferenceById였다면 여기서 걸리지 않고 flush 시점의 FK 위반 500이 된다
    @Test
    @DisplayName("판매자 계정이 없으면 차량을 만들기 전에 거부한다")
    void apply판매자_없음() {
        givenLookup(spec(IMAGE_URL));
        given(userRepository.findById(SELLER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(SellErrorCode.SELLER_NOT_FOUND);

        then(vehicleRepository).shouldHaveNoInteractions();
        then(auctionRepository).shouldHaveNoInteractions();
    }

    // 중복 검사를 넣지 않기로 한 결정을 고정한다, 매번 새 차량이라 AuctionPost의 unique 제약과 충돌하지 않는다
    @Test
    @DisplayName("같은 번호판으로 다시 신청해도 차량을 새로 만든다")
    void apply번호판_중복_허용() {
        givenLookup(spec(IMAGE_URL));
        givenSeller();
        givenSaveReturnsArgument();

        service.apply(command());
        service.apply(command());

        then(vehicleRepository).should(times(2)).save(any(Vehicle.class));
        then(auctionRepository).should(times(2)).save(any(Auction.class));
    }

    // ================= given =================

    private void givenLookup(VehicleSpec spec) {
        given(vehicleLookup.findByPlateNumber(PLATE_NUMBER)).willReturn(Optional.of(spec));
    }

    private void givenSeller() {
        given(userRepository.findById(SELLER_ID)).willReturn(Optional.of(mock(User.class)));
    }

    // save가 돌려주는 엔티티에는 id가 없으므로 반환 Info가 아니라 captor로 검증한다
    private void givenSaveReturnsArgument() {
        given(vehicleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(auctionPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(auctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private static SellApplicationCommand command() {
        return new SellApplicationCommand(SELLER_ID, PLATE_NUMBER, MILEAGE);
    }

    private static VehicleSpec spec(String mainImageUrl) {
        return new VehicleSpec(PLATE_NUMBER, "김민수",
                Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                BASE_PRICE, mainImageUrl);
    }

    // ================= 캡처 =================

    private AuctionPost capturedPost() {
        ArgumentCaptor<AuctionPost> captor = ArgumentCaptor.forClass(AuctionPost.class);
        then(auctionPostRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private Auction capturedAuction() {
        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        then(auctionRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
