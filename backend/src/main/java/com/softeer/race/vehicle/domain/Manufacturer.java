package com.softeer.race.vehicle.domain;

/**
 * 제조사와 화면·알림에 쓰는 한글 표기
 * <p>
 * 표기를 상수가 함께 들고 있어 제조사를 추가하면 표기도 강제로 따라온다.
 */
public enum Manufacturer {
    HYUNDAI("현대"),
    KIA("기아"),
    GENESIS("제네시스"),
    CHEVROLET("쉐보레"),
    RENAULT_KOREA("르노코리아"),
    KG_MOBILITY("KG모빌리티"),
    BMW("BMW"),
    MERCEDES_BENZ("벤츠"),
    AUDI("아우디"),
    VOLKSWAGEN("폭스바겐"),
    VOLVO("볼보"),
    TOYOTA("토요타"),
    LEXUS("렉서스"),
    HONDA("혼다"),
    NISSAN("닛산"),
    FORD("포드"),
    TESLA("테슬라"),
    MINI("미니"),
    PORSCHE("포르쉐"),
    LAND_ROVER("랜드로버"),
    JEEP("지프"),
    PEUGEOT("푸조");

    private final String label;

    Manufacturer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
