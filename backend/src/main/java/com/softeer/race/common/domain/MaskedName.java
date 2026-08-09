package com.softeer.race.common.domain;

/**
 * 가운데를 가린 실명
 */
public final class MaskedName {

    private static final String MASK = "*";
    private static final int MIN_LENGTH = 2;

    private final String value;

    // 생성자를 열어 두면 마스킹되지 않은 값을 담을 수 있어 이 타입의 보장이 사라진다
    private MaskedName(String value) {
        this.value = value;
    }

    /**
     * 실명을 받아 가운데를 가린 이름
     */
    public static MaskedName mask(String realName) {
        return new MaskedName(maskMiddle(realName));
    }

    public String value() {
        return value;
    }

    private static String maskMiddle(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름이 비어 있습니다.");
        }

        int len = name.length();

        // 가리려는 값이라 메시지에 원본을 담지 않는다, 예외는 로그에도 남는다
        if (len < MIN_LENGTH) {
            throw new IllegalArgumentException("이름이 두 글자보다 짧습니다, 길이 " + len);
        }

        String first = name.substring(0, 1);

        // 두 글자에 아래 일반 식을 쓰면 가릴 자리가 없어 원본이 그대로 나온다, 그래서 마지막 글자를 가린다
        if (len == MIN_LENGTH) {
            return first + MASK;
        }

        return first + MASK.repeat(len - 2) + name.substring(len - 1);
    }
}