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
 * 경매글 썸네일({@code AuctionPost.thumbnailUrl})은 건드리지 않는다. 경매는 평가가 끝나고 판매자가
 * 출품에 동의한 뒤에 만들어지므로, 그 시점에 {@code AuctionService.create}가
 * {@code findFirstByVehicleOrderBySortOrderAsc}로 여기서 저장한 첫 장을 집어 간다. 즉 순서가
 * 사진 등록 → 경매 생성이라 따로 갱신할 것이 없다.
 * <p>
 * 다만 지금 {@code SellService}는 판매 신청 즉시 경매글을 만든다. 그 과도기에는 사진을 바꿔도
 * 이미 만들어진 경매글의 썸네일이 옛 카탈로그 이미지로 남는다. 판매 신청을 평가 신청으로
 * 전환하면 사라지는 문제라 별도 대응을 두지 않았다.

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

        // 기존 사진을 전부 지운다. 판매 신청 때 넣어 둔 카탈로그 홍보 이미지가 여기서 사라진다 —
        // 남겨 두면 대표 이미지 규칙이 sortOrder 최솟값이라 실물 사진을 등록해도 홍보 이미지가
        // 계속 대표로 남는다. 실물이 들어온 이상 그 자리를 채우려던 임시값은 쓸모가 없다
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
