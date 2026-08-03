package com.softeer.race.vehicle.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.image.domain.ImageStorage;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleRepository;
import com.softeer.race.vehicle.exception.VehicleErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 시나리오
 * <ol>
 *   <li>보낸 순서대로 sortOrder를 매겨 저장하고, 기존 사진은 전부 지운다</li>
 *   <li>우리가 발급하지 않은 주소가 섞이면 아무것도 지우거나 저장하지 않는다</li>
 *   <li>없는 차량이면 404</li>
 * </ol>
 * <p>
 * 경매글 썸네일은 검증하지 않는다. 경매는 사진 등록 이후(출품 동의 시점)에 만들어지고, 그때
 * {@code AuctionService.create}가 여기서 저장한 첫 장을 대표 이미지로 집어 간다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("차량 사진 등록 서비스")
class VehicleImageServiceTest {

    private static final long VEHICLE_ID = 1000L;
    private static final String REAL_IMAGE_1 = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String REAL_IMAGE_2 = "https://cdn.race.dev/images/2026/08/b.jpg";

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleImageRepository vehicleImageRepository;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private VehicleImageService vehicleImageService;

    private Vehicle vehicle;

    @BeforeEach
    void before() {
        vehicle = mock(Vehicle.class);
    }

    @Test
    @DisplayName("보낸 순서대로 sortOrder를 매겨 저장하고 기존 사진은 전부 지운다")
    void register() {
        // given
        givenVehicleFound();
        givenAllUrlsManaged();
        givenSaveAllReturnsInput();
        given(vehicle.getId()).willReturn(VEHICLE_ID);

        // when
        VehicleImageRegisterInfo info = vehicleImageService.register(command(REAL_IMAGE_1, REAL_IMAGE_2));

        // then : 카탈로그 홍보 이미지를 남겨 두면 대표 이미지 규칙이 sortOrder 최솟값이라
        //        실물을 등록해도 홍보 이미지가 계속 대표로 남는다
        then(vehicleImageRepository).should().deleteAllByVehicle(vehicle);

        assertThat(info.images())
                .extracting(VehicleImageRegisterInfo.RegisteredImage::imageUrl)
                .containsExactly(REAL_IMAGE_1, REAL_IMAGE_2);
        assertThat(info.images())
                .extracting(VehicleImageRegisterInfo.RegisteredImage::sortOrder)
                .containsExactly(1, 2);
        assertThat(info.thumbnailUrl()).isEqualTo(REAL_IMAGE_1);
        assertThat(info.vehicleId()).isEqualTo(VEHICLE_ID);
    }

    @Test
    @DisplayName("발급하지 않은 주소가 섞이면 지우지도 저장하지도 않는다")
    void registerRejectsUnmanagedUrl() {
        // given : 첫 장은 우리 주소, 두 번째가 외부 주소다
        givenVehicleFound();
        given(imageStorage.isManagedUrl(REAL_IMAGE_1)).willReturn(true);
        given(imageStorage.isManagedUrl("https://evil.example.com/x.jpg")).willReturn(false);

        // when
        assertThatThrownBy(() ->
                vehicleImageService.register(command(REAL_IMAGE_1, "https://evil.example.com/x.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(VehicleErrorCode.UNMANAGED_IMAGE_URL);

        // then : 한 건씩 검증하며 저장하면 앞의 것만 반영된 채로 400이 나간다.
        //        특히 삭제가 먼저 일어나면 기존 사진까지 잃는다
        then(vehicleImageRepository).should(never()).deleteAllByVehicle(any());
        then(vehicleImageRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("없는 차량이면 NOT_FOUND")
    void registerRejectsUnknownVehicle() {
        // given
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> vehicleImageService.register(command(REAL_IMAGE_1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(VehicleErrorCode.NOT_FOUND);

        then(imageStorage).shouldHaveNoInteractions();
    }

    private void givenVehicleFound() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
    }

    private void givenAllUrlsManaged() {
        given(imageStorage.isManagedUrl(anyString())).willReturn(true);
    }

    @SuppressWarnings("unchecked")
    private void givenSaveAllReturnsInput() {
        given(vehicleImageRepository.saveAll(any()))
                .willAnswer(invocation -> List.copyOf((List<VehicleImage>) invocation.getArgument(0)));
    }

    private static VehicleImageRegisterCommand command(String... imageUrls) {
        return new VehicleImageRegisterCommand(VEHICLE_ID, List.of(imageUrls));
    }
}
