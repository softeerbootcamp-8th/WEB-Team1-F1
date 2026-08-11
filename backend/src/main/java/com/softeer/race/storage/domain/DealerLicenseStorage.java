package com.softeer.race.storage.domain;

/** 가입 전 자동차매매사원증을 비공개 객체로 업로드하고, 가입 시 실제 업로드를 확인한다. */
public interface DealerLicenseStorage {

    PresignedDealerLicense presignDealerLicense(UploadContentType contentType, long contentLength);

    /**
     * 전용 키 형식, 객체 존재 여부, Content-Type과 크기를 모두 확인한다.
     * 저장소 장애는 정상적인 검증 실패와 구분해 예외로 전달한다.
     */
    boolean isValidUploadedDealerLicense(String key);
}
