package com.softeer.race.auctionroom.domain;

/**
 * 경매방에 실명 마스킹
 */
public record MaskedName(String value) {

    private static final String MASK = "*";
    private static final int MIN_LENGTH = 2;

    public MaskedName {
        value = mask(value);
    }

    private static String mask(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름이 비어 있습니다.");
        }

        int len = name.length();

        if (len < MIN_LENGTH) {
            throw new IllegalArgumentException("이름이 두 글자 이상이어야 합니다.");
        }

        String first = name.substring(0, 1);

        if (len == MIN_LENGTH) {
            return first + MASK;
        }

        return first + MASK.repeat(len - 2) + name.substring(len - 1);
    }
}