package com.softeer.race.storage.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * S3 설정. 값이 없으면 record는 null로 바인딩되므로 필수 항목은 여기서 기동을 실패시킨다.
 * <p>
 * 누락을 기동 시점에 잡는 이유는, 통과시키면 첫 업로드 요청에서야 NPE나 알아보기 힘든 SDK 예외로
 * 드러나기 때문이다. 그 시점에는 원인이 설정 누락이라는 게 보이지 않는다.
 *
 * @param bucket        버킷 이름
 * @param region        리전
 * @param cdnBaseUrl 조회용 베이스 주소. <b>업로드용 S3 호스트가 아니라 CloudFront 도메인</b>이다.
 *                      업로드는 서명이 S3 호스트에 묶여 있어 그쪽으로 직접 보내고, 조회만 CDN을 탄다
 * @param presignExpiry 발급한 주소의 유효 기간
 * @param endpoint      S3 대신 호출할 주소. <b>비워 두는 것이 정상이고</b> 그때 실제 S3로 간다.
 *                      로컬 개발에서 S3 호환 서버(MinIO)를 쓸 때만 채운다
 * @param accessKey     {@code endpoint}를 채웠을 때 서명에 쓸 키. 실제 S3에서는 쓰지 않는다
 * @param secretKey     같은 용도의 비밀 키
 */
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String bucket,
        String region,
        String cdnBaseUrl,
        Duration presignExpiry,
        String endpoint,
        String accessKey,
        String secretKey
) {

    private static final Duration DEFAULT_PRESIGN_EXPIRY = Duration.ofMinutes(15);

    /**
     * SigV4의 상한이다. 다만 EC2 인스턴스 역할처럼 임시 자격 증명으로 서명하면 <b>그 세션이 만료될 때
     * 주소도 함께 죽으므로</b> 실제 수명은 이보다 훨씬 짧다. 길게 잡아도 얻는 게 없다.
     */
    private static final Duration MAX_PRESIGN_EXPIRY = Duration.ofDays(7);

    public S3Properties {
        requireText(bucket, "aws.s3.bucket");
        requireText(region, "aws.s3.region");
        requireText(cdnBaseUrl, "aws.s3.cdn-base-url");

        // 키를 이어 붙일 때 //가 되지 않게 여기서 한 번만 정리한다
        cdnBaseUrl = cdnBaseUrl.endsWith("/")
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : cdnBaseUrl;

        presignExpiry = presignExpiry != null ? presignExpiry : DEFAULT_PRESIGN_EXPIRY;
        if (presignExpiry.isNegative() || presignExpiry.isZero()) {
            throw new IllegalArgumentException(
                    "aws.s3.presign-expiry는 0보다 커야 합니다. presignExpiry=%s".formatted(presignExpiry));
        }
        if (presignExpiry.compareTo(MAX_PRESIGN_EXPIRY) > 0) {
            throw new IllegalArgumentException(
                    "aws.s3.presign-expiry는 7일을 넘을 수 없습니다. presignExpiry=%s".formatted(presignExpiry));
        }

        // 셋 다 없는 것이 실제 S3를 쓰는 정상 상태다. endpoint만 있고 키가 없으면 기본 자격 증명
        // 탐색으로 넘어가 ~/.aws/credentials 를 집는데, 그 키로 MinIO에 서명하면 업로드가 403 이
        // 되고 원인이 설정 누락이라는 게 그 시점에는 보이지 않는다
        if (endpoint != null && !endpoint.isBlank()) {
            requireTextWithEndpoint(accessKey, "aws.s3.access-key");
            requireTextWithEndpoint(secretKey, "aws.s3.secret-key");
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s가 설정되지 않았습니다.".formatted(property));
        }
    }

    private static void requireTextWithEndpoint(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "aws.s3.endpoint를 지정했으면 %s도 필요합니다.".formatted(property));
        }
    }
}
