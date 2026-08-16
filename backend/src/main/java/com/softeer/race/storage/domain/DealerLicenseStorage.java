package com.softeer.race.storage.domain;

/** 가입 전 자동차매매사원증을 비공개 객체로 업로드하고, 가입 시 실제 업로드를 확인한다. */
public interface DealerLicenseStorage {

    PresignedDealerLicense presignDealerLicense(UploadContentType contentType, long contentLength);

    /**
     * 사원증을 볼 수 있는 임시 주소를 발급한다. 심사하는 관리자에게만 준다.
     * <p>
     * 차량 사진과 달리 CloudFront로 공개된 주소가 없다. 사원증은 신분증에 준하는 서류라 버킷이
     * 잠긴 채로 두고, 볼 필요가 있을 때만 짧게 사는 서명된 주소를 만든다. 주소가 새더라도
     * 만료되면 죽는다는 것이 이 방식의 요점이다.
     */
    PresignedDealerLicenseView presignDealerLicenseView(String key);

    /**
     * 전용 키 형식, 객체 존재 여부, Content-Type과 크기를 모두 확인한다.
     * 저장소 장애는 정상적인 검증 실패와 구분해 예외로 전달한다.
     */
    boolean isValidUploadedDealerLicense(String key);
}
