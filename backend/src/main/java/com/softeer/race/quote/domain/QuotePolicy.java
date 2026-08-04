package com.softeer.race.quote.domain;

/**
 * 기준가에 연식·주행거리 감가를 반영해 예상 시세를 산정한다.
 *
 * <p>조회기가 준 기준가를 그대로 내려주지 않고 서버가 다시 계산한다. 기준가가 노출되면
 * 산정 로직이 역산되고, 조회기가 바뀔 때마다 시세 기준이 함께 흔들린다.
 *
 * <p>계수는 실제 거래 데이터 없이 잡은 임시값이다. 외부 시세 API가 붙으면 이 클래스는 사라지므로
 * 곡선을 정교하게 맞추는 데 시간을 쓰지 않았다. 손봐야 하는 신호는 "특정 연식대 시세가
 * 눈에 띄게 이상하다"는 데모 피드백이다.
 *
 * <p>사고 이력은 아직 반영하지 않는다. 지금 조회기가 그 정보를 주지 못하기 때문이고,
 * 외부 연동으로 들어오면 감가 항목을 하나 더 더하는 변경이 된다.
 */
public final class QuotePolicy {

    // 천분율로 계산한다. 금액에 double 을 쓰면 반올림 오차가 쌓이고,
    // 백분율 정수로는 1.5% 같은 값을 표현할 수 없다.
    private static final long PER_MILLE = 1_000L;

    /** 연식 1년당 기준가의 5%, 5년 된 차가 기준가의 75% 수준이 되도록 잡았다 */
    private static final long AGE_RATE = 50L;

    /** 주행 1만km당 기준가의 1.5% */
    private static final long MILEAGE_RATE = 15L;

    private static final long MILEAGE_UNIT_KM = 10_000L;

    /**
     * 감가를 다 빼도 이 아래로는 내려가지 않는다, 기준가의 20%.
     * 하한을 0 으로 두지 않는 이유는 시세 0원인 차는 시작가를 정할 수 없어 경매에 올릴 수 없기 때문이다.
     */
    private static final long FLOOR_RATE = 200L;

    /** 원 단위까지 보여주면 정밀해 보이지만 근거가 없다, 만원 단위로 내린다 */
    private static final long DISPLAY_UNIT = 10_000L;

    private QuotePolicy() {
    }

    /**
     * 연식 기준 나이(년). {@link #estimate}의 age 인자를 만드는 유일한 경로다.
     *
     * <p>등록월까지 반영하면 정밀해 보이지만 카탈로그에 월 정보가 없고, 감가율 자체가 임시값이라
     * 정밀도를 올려도 정확도가 오르지 않는다. 그래서 연도 차이로만 센다.
     *
     * <p>계산 자체는 한 줄이지만 여기 모아 둔다. 시세 조회 · 판매 신청 · 방문견적 신청 세 곳이 같은
     * 값을 계산하고, 한 곳만 등록월을 반영하도록 바뀌면 같은 차의 예상 시세가 화면마다 갈라진다.
     *
     * @param currentYear 호출자가 주입된 Clock으로 읽은 현재 연도
     */
    public static int ageOf(int modelYear, int currentYear) {
        return currentYear - modelYear;
    }

    /**
     * 예상 시세를 원 단위로 돌려준다. 만원 단위로 내려져 있다.
     *
     * @param basePrice 조회기가 준 기준가
     * @param age       연식 기준 나이(년)
     * @param mileage   주행거리(km)
     */
    public static long estimate(long basePrice, int age, int mileage) {
        // 시드에 출고 예정 연식이나 음수 주행거리가 들어가면 감가가 가산으로 뒤집혀
        // 기준가보다 높은 시세가 나온다. 서버가 만든 값이 아니라 데이터 쪽 사고라 막고 넘어간다.
        long safeAge = Math.max(age, 0);
        long safeMileage = Math.max(mileage, 0);

        long ageDeduction = basePrice * AGE_RATE * safeAge / PER_MILLE;
        long mileageDeduction =
                basePrice * MILEAGE_RATE * safeMileage / (PER_MILLE * MILEAGE_UNIT_KM);

        long depreciated = basePrice - ageDeduction - mileageDeduction;
        long floor = roundDown(basePrice * FLOOR_RATE / PER_MILLE);

        // 하한을 먼저 내려두고 비교한다, 비교한 뒤에 내리면 결과가 하한보다 낮아질 수 있다
        return Math.max(roundDown(depreciated), floor);
    }

    private static long roundDown(long price) {
        return price / DISPLAY_UNIT * DISPLAY_UNIT;
    }
}