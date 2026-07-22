package com.softeer.race.sample.domain;

public class Sample {

    private final String name;

    public Sample(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 비어 있을 수 없습니다.");
        }
        this.name = name;
    }

    public String greeting() {
        return "Hello, " + name + "!";
    }
}
