package com.softeer.race.quote.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.quote.application.dto.command.QuoteCommand;
import com.softeer.race.quote.application.dto.info.QuoteInfo;
import com.softeer.race.quote.domain.QuotePolicy;
import com.softeer.race.quote.exception.QuoteErrorCode;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleSpec;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번호판과 소유자명으로 차량 제원을 조회해 예상 시세를 산정한다. 인증이 필요 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteService {

    private final VehicleLookup vehicleLookup;
    private final Clock clock;

    public QuoteInfo estimate(QuoteCommand command) {
        // 미등록과 소유자명 불일치를 같은 에러로 번역한다. 포트가 둘을 구분해 주지 않으므로
        // 여기서 갈라놓을 방법 자체가 없고, 그게 의도다
        VehicleSpec spec = vehicleLookup.find(command.plateNumber(), command.ownerName())
                .orElseThrow(() -> new BusinessException(QuoteErrorCode.QUOTE_VEHICLE_NOT_FOUND));

        // 나이는 연도 차이로만 센다. 등록월까지 반영하면 정밀해 보이지만 카탈로그에 월 정보가 없고,
        // 감가율 자체가 임시값이라 정밀도를 올려도 정확도가 오르지 않는다
        int age = LocalDate.now(clock).getYear() - spec.modelYear();

        return QuoteInfo.of(spec, QuotePolicy.estimate(spec.basePrice(), age, spec.mileage()));
    }
}
