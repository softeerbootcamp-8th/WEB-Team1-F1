package com.softeer.race.vehicle.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.vehicle.application.dto.command.VehicleLookupCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleLookupInfo;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.exception.VehicleErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번호판과 소유자명으로 차량 제원을 찾아 사용자에게 확인시킨다. 인증이 필요 없다.
 * <p>
 * 시세 조회와 방문견적 신청의 공통 첫 단계다. 이 조회가 없으면 제원을 얻는 유일한 경로가 시세
 * 조회뿐이라, 사용자가 자기 차인지 확인하기 전에 주행거리를 먼저 입력해야 하고, 시세가 필요 없는
 * 방문견적 화면도 시세 조회를 호출해야 한다.
 * <p>
 * <b>{@link VehicleLookup}과 이 클래스는 다른 것이다.</b> 그쪽은 외부 차량정보 서비스를 감싼
 * domain 의 포트이고, 이 클래스는 그 포트를 쓰는 유스케이스다. 이름이 가까워 파일을 열 때
 * 헛갈리기 쉬우니 주의한다.
 * <p>
 * 예상 시세를 산정하지 않는다. 주행거리를 모르는 상태에서는 계산할 수 없고, {@code Clock}을
 * 주입받지 않는 것도 그래서다 — 연식 나이를 셀 일이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleLookupService {

    private final VehicleLookup vehicleLookup;

    /**
     * 번호판과 소유자명이 모두 맞는 차량의 제원을 돌려준다.
     */
    public VehicleLookupInfo lookup(VehicleLookupCommand command) {
        // 소유자명까지 대조하는 find 를 쓴다. 인증이 없는 조회라 번호판만으로 찾을 수 있게 하면
        // 번호판을 바꿔 넣어보며 소유자명을 알아낼 수 있다(findByPlateNumber 를 쓰면 안 되는 이유)
        //
        // 미등록과 소유자명 불일치를 같은 에러로 번역한다. 포트가 둘을 구분해 주지 않으므로
        // 여기서 갈라놓을 방법 자체가 없고, 그게 의도다
        return vehicleLookup.find(command.plateNumber(), command.ownerName())
                .map(VehicleLookupInfo::from)
                .orElseThrow(() -> new BusinessException(VehicleErrorCode.SPEC_NOT_FOUND));
    }
}
