package com.softeer.race.storage.infrastructure;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.PresignedDealerLicense;
import com.softeer.race.storage.domain.PresignedDealerLicenseView;
import com.softeer.race.storage.exception.StorageErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code isManagedUrl}은 클라이언트가 돌려준 주소를 저장하기 전 마지막 관문이라, 여기가 느슨하면
 * 로그인한 사용자가 우리가 서명해 준 적 없는 객체를 차량 이미지로 박아 넣을 수 있다. 그래서 통과
 * 사례보다 거부 사례를 넓게 둔다.
 * <p>
 * 종류가 생긴 뒤로는 <b>교차 사례가 특히 중요하다.</b> 진단서 PDF도 우리가 발급한 주소라, 종류를
 * 보지 않으면 그대로 차량 사진이 된다. "우리가 발급했다"와 "여기에 넣을 수 있다"가 다른 판정이라는
 * 것을 교차 사례가 지킨다.
 * <p>
 * 서명 발급은 실제 자격 증명을 요구하므로 여기서 다루지 않는다. {@code presigner}는 이 판정에
 * 쓰이지 않아 목으로 채운다.
 */
class S3FileStorageTest {

    private static final String CDN_BASE_URL = "https://cdn.example.com";
    private static final String UUID_NAME = "123e4567-e89b-12d3-a456-426614174000";
    private static final String IMAGE_URL = CDN_BASE_URL + "/images/2026/08/" + UUID_NAME;
    private static final String DOCUMENT_URL = CDN_BASE_URL + "/documents/2026/08/" + UUID_NAME;
    private static final String DEALER_LICENSE_KEY =
            "dealer-licenses/2026/08/" + UUID_NAME + ".jpg";

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final S3Client s3Client = mock(S3Client.class);

    private final S3FileStorage s3FileStorage = new S3FileStorage(
            s3Presigner,
            s3Client,
            // 뒤의 셋(endpoint, accessKey, secretKey)은 로컬 개발용이라 비운다, 그 상태가 배포 설정이다
            new S3Properties("bucket", "ap-northeast-2", CDN_BASE_URL, Duration.ofMinutes(15),
                    null, null, null),
            Clock.systemDefaultZone());

    @DisplayName("이미지로 발급한 키 형태의 주소는 이미지 판정을 통과한다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            IMAGE_URL + ".jpg",
            IMAGE_URL + ".png",
            IMAGE_URL + ".webp"
    })
    void acceptIssuedImageUrl(String fileUrl) {
        assertThat(s3FileStorage.isManagedUrl(fileUrl, FileCategory.IMAGE)).isTrue();
    }

    @DisplayName("문서로 발급한 키 형태의 주소는 문서 판정을 통과한다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {DOCUMENT_URL + ".pdf"})
    void acceptIssuedDocumentUrl(String fileUrl) {
        assertThat(s3FileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)).isTrue();
    }

    /**
     * 우리가 발급한 주소인데도 떨어져야 하는 것들이다. 접두사와 확장자가 짝을 이뤄야 하므로
     * {@code documents/…/x.jpg}처럼 한쪽만 맞는 값도 통과하지 않는다 — 그런 키는 발급된 적이 없다.
     */
    @DisplayName("종류가 다른 주소는 거부한다")
    @ParameterizedTest(name = "[{1}] {0}")
    @CsvSource({
            DOCUMENT_URL + ".pdf, IMAGE",
            IMAGE_URL + ".jpg, DOCUMENT",
            IMAGE_URL + ".pdf, IMAGE",
            DOCUMENT_URL + ".jpg, DOCUMENT",
            IMAGE_URL + ".pdf, DOCUMENT",
            DOCUMENT_URL + ".jpg, IMAGE"
    })
    void rejectOtherCategoryUrl(String fileUrl, FileCategory category) {
        assertThat(s3FileStorage.isManagedUrl(fileUrl, category)).isFalse();
    }

    /**
     * {@code %2e%2e}는 이 검증을 문자열 비교로 두면 그대로 빠져나간다. S3가 키를 리터럴로 다뤄
     * 실제 경로 이동이 일어나지는 않지만, 발급하지 않은 객체를 가리키는 것은 막아야 한다.
     */
    @DisplayName("발급하지 않은 주소는 거부한다")
    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {
            "https://evil.example.com/images/2026/08/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/../other/2026/08/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/%2e%2e/other/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/assets/2026/08/" + UUID_NAME + ".jpg",
            IMAGE_URL + ".gif",
            IMAGE_URL,
            IMAGE_URL + ".jpg?x=1",
            IMAGE_URL + ".jpg#fragment",
            CDN_BASE_URL + "/images/2026/08/not-a-uuid.jpg",
            CDN_BASE_URL + "/images/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/2026/08/" + UUID_NAME + ".jpg/extra",
            // 발급 결과가 낼 수 없는 값들. LocalDate는 00이나 13월을 만들지 않고
            // UUID.toString()은 대문자 hex를 만들지 않는다
            CDN_BASE_URL + "/images/2026/00/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/2026/13/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/2026/08/123E4567-E89B-12D3-A456-426614174000.jpg"
    })
    void rejectUnissuedImageUrl(String fileUrl) {
        assertThat(s3FileStorage.isManagedUrl(fileUrl, FileCategory.IMAGE)).isFalse();
    }

    @DisplayName("문서 판정도 같은 규칙으로 좁혀져 있다")
    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {
            "https://evil.example.com/documents/2026/08/" + UUID_NAME + ".pdf",
            CDN_BASE_URL + "/documents/%2e%2e/other/" + UUID_NAME + ".pdf",
            DOCUMENT_URL,
            DOCUMENT_URL + ".pdf?x=1",
            CDN_BASE_URL + "/documents/2026/13/" + UUID_NAME + ".pdf"
    })
    void rejectUnissuedDocumentUrl(String fileUrl) {
        assertThat(s3FileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)).isFalse();
    }

    @Test
    @DisplayName("사원증은 형태가 맞아도 공개 조회 URL로 인정하지 않는다")
    void rejectDealerLicensePublicUrl() {
        assertThat(s3FileStorage.isManagedUrl(
                CDN_BASE_URL + "/" + DEALER_LICENSE_KEY, FileCategory.DEALER_LICENSE)).isFalse();
    }

    @Test
    @DisplayName("사원증은 전용 경로에 발급하고 외부 조회 URL을 만들지 않는다")
    void presignDealerLicense() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.example.com/upload").toURL());
        when(presigned.expiration()).thenReturn(Instant.parse("2026-08-11T03:00:00Z"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignedDealerLicense result = s3FileStorage.presignDealerLicense(
                com.softeer.race.storage.domain.UploadContentType.JPEG, 1024L);

        assertThat(result.key()).startsWith("dealer-licenses/").endsWith(".jpg");
        assertThat(result.uploadUrl()).isEqualTo("https://s3.example.com/upload");
    }

    @Test
    @DisplayName("사원증 조회 주소는 서명된 임시 주소로 발급한다")
    void presignDealerLicenseView() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.example.com/view").toURL());
        when(presigned.expiration()).thenReturn(Instant.parse("2026-08-11T03:00:00Z"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        PresignedDealerLicenseView result =
                s3FileStorage.presignDealerLicenseView(DEALER_LICENSE_KEY);

        assertThat(result.viewUrl()).isEqualTo("https://s3.example.com/view");
    }

    // 서명해 주는 순간 그 객체를 읽을 수 있는 주소가 된다. 발급한 적 없는 키에는 서명이 나가면 안 된다
    @Test
    @DisplayName("발급한 적 없는 형태의 키에는 조회 주소를 서명하지 않는다")
    void rejectUnmanagedKeyOnView() {
        assertThatThrownBy(() -> s3FileStorage.presignDealerLicenseView("dealer-licenses/../secret"))
                .isInstanceOf(BusinessException.class);

        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("실제로 업로드된 사원증의 형식과 크기가 맞으면 유효하다")
    void validateUploadedDealerLicense() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(1024L)
                        .build());

        assertThat(s3FileStorage.isValidUploadedDealerLicense(DEALER_LICENSE_KEY)).isTrue();
    }

    @Test
    @DisplayName("전용 키가 아니면 S3를 조회하지 않고 거부한다")
    void rejectInvalidDealerLicenseKey() {
        assertThat(s3FileStorage.isValidUploadedDealerLicense(
                "documents/2026/08/" + UUID_NAME + ".pdf")).isFalse();
    }

    @Test
    @DisplayName("키 확장자와 실제 Content-Type이 다르면 거부한다")
    void rejectMismatchedDealerLicenseMetadata() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/png")
                        .contentLength(1024L)
                        .build());

        assertThat(s3FileStorage.isValidUploadedDealerLicense(DEALER_LICENSE_KEY)).isFalse();
    }

    @Test
    @DisplayName("실제 객체가 10MB를 넘으면 거부한다")
    void rejectOversizedDealerLicenseObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(10L * 1024 * 1024 + 1)
                        .build());

        assertThat(s3FileStorage.isValidUploadedDealerLicense(DEALER_LICENSE_KEY)).isFalse();
    }

    @Test
    @DisplayName("S3에 객체가 없으면 유효하지 않은 업로드로 처리한다")
    void rejectMissingDealerLicense() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).message("not found").build());

        assertThat(s3FileStorage.isValidUploadedDealerLicense(DEALER_LICENSE_KEY)).isFalse();
    }

    @Test
    @DisplayName("S3 장애는 저장소 사용 불가 예외로 구분한다")
    void translateStorageFailure() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(503).message("unavailable").build());

        assertThatThrownBy(() -> s3FileStorage.isValidUploadedDealerLicense(DEALER_LICENSE_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(StorageErrorCode.STORAGE_UNAVAILABLE));
    }
}
