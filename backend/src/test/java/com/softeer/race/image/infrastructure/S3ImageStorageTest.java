package com.softeer.race.image.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code isManagedUrl}은 클라이언트가 돌려준 주소를 저장하기 전 마지막 관문이라, 여기가 느슨하면
 * 로그인한 사용자가 우리가 서명해 준 적 없는 객체를 차량 이미지로 박아 넣을 수 있다. 그래서 통과
 * 사례보다 거부 사례를 넓게 둔다.
 * <p>
 * 서명 발급은 실제 자격 증명을 요구하므로 여기서 다루지 않는다. {@code presigner}는 이 판정에
 * 쓰이지 않아 목으로 채운다.
 */
class S3ImageStorageTest {

    private static final String CDN_BASE_URL = "https://cdn.example.com";
    private static final String UUID_NAME = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ISSUED_URL = CDN_BASE_URL + "/images/2026/08/" + UUID_NAME;

    private final S3ImageStorage s3ImageStorage = new S3ImageStorage(
            mock(S3Presigner.class),
            new S3Properties("bucket", "ap-northeast-2", CDN_BASE_URL, Duration.ofMinutes(15)),
            Clock.systemDefaultZone());

    @DisplayName("발급한 키 형태의 주소는 통과한다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            ISSUED_URL + ".jpg",
            ISSUED_URL + ".png",
            ISSUED_URL + ".webp"
    })
    void acceptIssuedUrl(String fileUrl) {
        assertThat(s3ImageStorage.isManagedUrl(fileUrl)).isTrue();
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
            ISSUED_URL + ".gif",
            ISSUED_URL,
            ISSUED_URL + ".jpg?x=1",
            ISSUED_URL + ".jpg#fragment",
            CDN_BASE_URL + "/images/2026/08/not-a-uuid.jpg",
            CDN_BASE_URL + "/images/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/2026/08/" + UUID_NAME + ".jpg/extra",
            // 발급 결과가 낼 수 없는 값들. LocalDate는 00이나 13월을 만들지 않고
            // UUID.toString()은 대문자 hex를 만들지 않는다
            CDN_BASE_URL + "/images/2026/00/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/2026/13/" + UUID_NAME + ".jpg",
            CDN_BASE_URL + "/images/2026/08/123E4567-E89B-12D3-A456-426614174000.jpg"
    })
    void rejectUnissuedUrl(String fileUrl) {
        assertThat(s3ImageStorage.isManagedUrl(fileUrl)).isFalse();
    }
}
