package com.softeer.race.vehicle.application;

import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.vehicle.application.dto.info.DemoVehicleInfo;
import com.softeer.race.vehicle.infrastructure.DemoVehicleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시세 조회와 방문견적 신청에 넣어 볼 수 있는 데모 차량을 안내한다. 인증이 필요 없다.
 * <p>
 * 안내가 신청과 다른 기준을 쓰면 "도움말에는 있는데 넣으면 거절"이 생긴다. 사용자가 고칠 수 없는
 * 실패라, 진행 중 판정을 여기서 다시 정의하지 않고 {@code EvaluationStatus.inProgress()}를 그대로 읽는다.
 * <p>
 * 조회와 신청 사이에 남이 먼저 신청하는 시간차는 남는다. 어떤 방식으로도 없앨 수 없고,
 * 그 순간의 실패는 안내가 만든 것이 아니라 경합이 만든 것이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemoVehicleService {

    // 도움말은 훑어보는 표라 페이지네이션이 없다. 화면이 아니라 서버가 자른다
    private static final int MAX_SIZE = 10;

    private final DemoVehicleRepository demoVehicleRepository;

    /**
     * 지금 쓸 수 있는 데모 차량. 없으면 빈 목록이고 예외가 아니다.
     */
    public List<DemoVehicleInfo> list() {
        return demoVehicleRepository
                .findAvailable(EvaluationStatus.inProgress(), Limit.of(MAX_SIZE))
                .stream()
                .map(DemoVehicleInfo::from)
                .toList();
    }
}