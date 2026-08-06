package com.softeer.race.vehicle.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleRepository;
import com.softeer.race.vehicle.exception.VehicleErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 차량 사진 등록. 이미 저장소에 올라간 주소들을 차량에 붙인다.
 * <p>
 * 대표 사진은 여기서 정하지 않는다.
 * 평가사가 고른 값을 Vehicle.completeDiagnosis 으로 받으므로 이 서비스는 갤러리만 저장한다.
 * <p>
 * 이 서비스에는 컨트롤러가 없다. 사진만 따로 바꾸는 입구를 두면 {@code vehicleId}만으로는
 * "배정된 평가사인가"를 물을 수 없어 인가를 걸 방법이 없다. 유일한 호출자는 평가 결과 제출
 * ({@code EvaluationResultService})이고, 그쪽은 {@code evaluationId} 축이라 그 질문이 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleImageService {

    private static final int FIRST_SORT_ORDER = 1;

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final FileStorage fileStorage;

    /**
     * 차량의 사진을 받은 목록으로 통째로 교체한다.
     */
    @Transactional
    public VehicleImageRegisterInfo register(VehicleImageRegisterCommand command) {
        Vehicle vehicle = vehicleRepository.findById(command.vehicleId())
                .orElseThrow(() -> new BusinessException(VehicleErrorCode.NOT_FOUND));

        // 하나라도 남의 주소면 아무것도 저장하지 않는다. 한 건씩 검증하며 저장하면
        // 목록 중간에서 400이 날 때 앞의 것들만 반영된 상태가 남는다
        command.imageUrls().forEach(this::validateManaged);

        // 재제출이면 앞서 올린 목록을 통째로 갈아 끼운다, 평가사가 낸 목록이 그 차량 사진의 전부다
        vehicleImageRepository.deleteAllByVehicle(vehicle);

        List<VehicleImage> saved = vehicleImageRepository.saveAll(numbered(vehicle, command.imageUrls()));

        return VehicleImageRegisterInfo.from(vehicle.getId(), saved);
    }

    /**
     * 종류를 {@code IMAGE}로 못 박는다. "우리가 발급한 주소인가"만 물으면 진단서 PDF도 우리가
     * 발급한 것이라 통과해, <b>차량 사진 자리에 문서가 등록된다.</b> 그러면 목록의 첫 장이 대표
     * 이미지가 되는 규칙 때문에 경매글 썸네일이 PDF 주소가 될 수도 있다.
     */
    private void validateManaged(String imageUrl) {
        if (!fileStorage.isManagedUrl(imageUrl, FileCategory.IMAGE)) {
            throw new BusinessException(VehicleErrorCode.UNMANAGED_IMAGE_URL);
        }
    }

    /**
     * 보낸 순서를 그대로 sortOrder로 매긴다. 클라이언트가 화면에서 정렬한 순서가 곧 표시 순서다.
     */
    private List<VehicleImage> numbered(Vehicle vehicle, List<String> imageUrls) {
        return IntStream.range(0, imageUrls.size())
                .mapToObj(index ->
                        VehicleImage.create(vehicle, imageUrls.get(index), FIRST_SORT_ORDER + index))
                .toList();
    }
}
