package com.softeer.race.storage.domain;

/**
 * 올라온 파일이 무엇으로 쓰이는지. 차량 사진과 진단서 PDF를 갈라놓는다.
 * <p>
 * 이 구분이 필요한 이유는 <b>등록 단계에서 종류를 되물어야 하기 때문</b>이다. 저장소는 "우리가
 * 발급한 주소인가"만 답할 수 있었는데, 그것만으로는 진단서 PDF를 차량 사진 자리에 등록하는 것을
 * 막지 못한다. 둘 다 우리가 발급한 주소이기 때문이다. 그래서 판정을 "우리가 발급한 <b>이미지</b>
 * 주소인가"로 좁힐 수 있게 종류를 값으로 만든다.
 * <p>
 * 키 접두사를 여기 두는 것은 그 판정을 접두사로 하기 때문이다. 종류별로 접두사가 갈라져 있어야
 * 주소만 보고 어느 쪽인지 알 수 있고, 나중에 문서만 CDN 공개에서 제외하는 것 같은 조치도
 * 경로 단위로 걸 수 있다.
 */
public enum FileCategory {

    IMAGE("images"),
    DOCUMENT("documents");

    private final String keyPrefix;

    FileCategory(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
