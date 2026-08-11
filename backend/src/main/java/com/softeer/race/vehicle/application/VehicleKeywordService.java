package com.softeer.race.vehicle.application;

import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import com.softeer.race.vehicle.domain.VehicleKeywordRow;
import com.softeer.race.vehicle.domain.VehicleKeywordTag;
import com.softeer.race.vehicle.domain.VehicleKeywordTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 차량에 매겨진 키워드 읽기와 교체.
 * <p>
 * {@link VehicleImageService}처럼 컨트롤러가 없다. 키워드만 따로 바꾸는 입구를 두면 {@code vehicleId}
 * 만으로는 "배정된 평가사인가"를 물을 수 없어 인가를 걸 방법이 없다. 쓰는 경로는 평가 결과 제출뿐이고,
 * 그쪽은 {@code evaluationId} 축이라 그 질문이 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleKeywordService {

    private final VehicleKeywordTagRepository vehicleKeywordTagRepository;

    /**
     * 차량의 키워드를 받은 목록으로 통째로 교체한다. 빈 목록이면 전부 지우고 끝난다.
     * <p>
     * 중복을 걸러 낸다. {@code (vehicle_id, keyword)}에 유니크 제약이 있어 같은 값이 두 번 오면
     * 제약 위반이 최후방 핸들러의 500이 된다. 요청 검증으로 거부하지 않는 것은 중복이 거부할 만한
     * 잘못이 아니기 때문이다 — 같은 키워드를 두 번 보낸 것과 한 번 보낸 것의 결과가 같아야 한다.
     * <p>
     * 차량을 다시 조회하지 않고 엔티티로 받는다. 유일한 호출자가 이미 잠그고 읽은 차량을 들고 있어,
     * id로 받으면 같은 행을 한 번 더 읽게 된다.
     */
    @Transactional
    public List<VehicleKeyword> replace(Vehicle vehicle, List<VehicleKeyword> keywords) {
        vehicleKeywordTagRepository.deleteAllByVehicle(vehicle);

        // 지운 것을 넣기 전에 반드시 내보낸다. 삭제는 커밋 때까지 미뤄지지만 식별자 전략이
        // IDENTITY 라 저장은 INSERT 를 즉시 내므로, 이 flush 가 없으면 아직 지워지지 않은 행과
        // 같은 (vehicle_id, keyword) 를 넣어 유니크 제약을 위반한다 — 같은 키워드를 그대로 다시
        // 제출하는 것이 정상 흐름이라 재제출이 전부 500 이 된다.
        // 사진 교체(VehicleImageService)에는 이 문제가 없다. 그쪽 테이블에는 유니크 제약이 없어
        // 순서가 뒤바뀌어도 조용히 통과했을 뿐이다
        vehicleKeywordTagRepository.flush();

        List<VehicleKeyword> replaced = sorted(keywords.stream().distinct().toList());

        vehicleKeywordTagRepository.saveAll(replaced.stream()
                .map(keyword -> VehicleKeywordTag.create(vehicle, keyword))
                .toList());

        return replaced;
    }

    /**
     * 이 차량의 키워드. 아직 매겨지지 않았으면 빈 목록이다.
     */
    public List<VehicleKeyword> findByVehicle(Vehicle vehicle) {
        return sorted(vehicleKeywordTagRepository.findAllByVehicle(vehicle).stream()
                .map(VehicleKeywordTag::getKeyword)
                .toList());
    }

    /**
     * 선언 순서로 정렬한다. enum 의 자연 순서가 곧 선언 순서다.
     * <p>
     * 읽는 쪽에 맡기지 않고 여기서 끝낸다. 같은 차량의 키워드가 화면마다 다른 순서로 보이면
     * 화면을 옮길 때 키워드가 뒤바뀐 것처럼 보인다.
     */
    private static List<VehicleKeyword> sorted(List<VehicleKeyword> keywords) {
        return keywords.stream().sorted(Comparator.naturalOrder()).toList();
    }

    public Map<Long, List<VehicleKeyword>> findByVehicleIds(Collection<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }

        return vehicleKeywordTagRepository.findRowsByVehicleIdIn(vehicleIds).stream()
                .collect(Collectors.groupingBy(
                                VehicleKeywordRow::vehicleId,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(VehicleKeywordRow::keyword, Collectors.toList()),
                                        VehicleKeywordService::sorted)
                        )
                );
    }
}
