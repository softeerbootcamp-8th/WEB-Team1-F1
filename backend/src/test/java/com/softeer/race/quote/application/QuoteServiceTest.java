package com.softeer.race.quote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.quote.application.dto.command.QuoteCommand;
import com.softeer.race.quote.application.dto.info.QuoteInfo;
import com.softeer.race.quote.exception.QuoteErrorCode;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleSpec;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 조회기를 가짜로 바꿔 서비스만 검증한다. 포트를 분리한 덕에 DB 없이 돌아간다.
 */
class QuoteServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 8, 3, 12, 0).atZone(KST).toInstant(), KST);

    private static final VehicleSpec GRANDEUR = new VehicleSpec(
            "12가3456", "김민수", Manufacturer.HYUNDAI, "그랜저 IG", 2021, 45_000,
            FuelType.GASOLINE, Transmission.AUTOMATIC,
            34_000_000L, "https://cdn.race.dev/vehicles/grandeur-ig.jpg");

    @DisplayName("조회한 제원으로 예상 시세를 산정해 함께 내려준다")
    @Test
    void estimatesFromLookedUpSpec() {
        // given
        QuoteService service = new QuoteService(lookupReturning(GRANDEUR), FIXED_CLOCK);

        // when
        QuoteInfo info = service.estimate(new QuoteCommand("12가3456", "김민수"));

        // then 1 : 기준가 3400만에서 5년·4.5만km 감가
        assertThat(info.estimatedPrice()).isEqualTo(23_200_000L);

        // then 2 : 제원은 조회한 값을 그대로 옮긴다
        assertThat(info.manufacturer()).isEqualTo(Manufacturer.HYUNDAI);
        assertThat(info.model()).isEqualTo("그랜저 IG");
        assertThat(info.modelYear()).isEqualTo(2021);
        assertThat(info.mileage()).isEqualTo(45_000);
    }

    // 조회기가 준 기준가를 그대로 내려주지 않는다는 결정을 고정한다
    @DisplayName("예상 시세는 조회기가 준 기준가와 다른 값이다")
    @Test
    void doesNotPassBasePriceThrough() {
        // given
        QuoteService service = new QuoteService(lookupReturning(GRANDEUR), FIXED_CLOCK);

        // when
        QuoteInfo info = service.estimate(new QuoteCommand("12가3456", "김민수"));

        // then
        assertThat(info.estimatedPrice()).isNotEqualTo(GRANDEUR.basePrice());
    }

    // 나이를 시스템 시각으로 세면 해가 바뀔 때 테스트가 조용히 깨진다
    @DisplayName("나이는 주입받은 Clock 의 연도에서 연식을 뺀 값이다")
    @Test
    void agesFromInjectedClock() {
        // given : 같은 차를 1년 뒤 시각으로 조회한다
        Clock nextYear = Clock.fixed(
                LocalDateTime.of(2027, 8, 3, 12, 0).atZone(KST).toInstant(), KST);
        QuoteService service = new QuoteService(lookupReturning(GRANDEUR), nextYear);

        // when
        QuoteInfo info = service.estimate(new QuoteCommand("12가3456", "김민수"));

        // then : 6년치가 되어 연식 감가가 기준가의 5%만큼 더 빠진다
        assertThat(info.estimatedPrice()).isEqualTo(21_500_000L);
    }

    @DisplayName("조회에 실패하면 404 로 번역되는 예외를 던진다")
    @Test
    void translatesEmptyToNotFound() {
        // given : 미등록이든 소유자명 불일치든 조회기는 빈 값만 준다
        QuoteService service = new QuoteService(lookupReturning(null), FIXED_CLOCK);

        // when & then
        assertThatThrownBy(() -> service.estimate(new QuoteCommand("99저9999", "김민수")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(QuoteErrorCode.QUOTE_VEHICLE_NOT_FOUND);
    }

    /**
     * 소유자명을 받지 않는 조회를 쓰면 실패한다.
     * <p>
     * 시세 조회는 인증이 없어서 번호판만으로 조회하면 대입으로 소유자명을 알아낼 수 있다.
     * 나중에 누가 findByPlateNumber 로 갈아끼우면 이 테스트가 잡는다.
     */
    private static VehicleLookup lookupReturning(VehicleSpec spec) {
        return new VehicleLookup() {

            @Override
            public Optional<VehicleSpec> findByPlateNumber(String plateNumber) {
                throw new AssertionError("시세 조회는 소유자명 없는 조회를 쓸 수 없다");
            }

            @Override
            public Optional<VehicleSpec> find(String plateNumber, String ownerName) {
                return Optional.ofNullable(spec);
            }
        };
    }
}
