package com.softeer.race.image.domain;

import java.time.LocalDateTime;

/**
 * 발급된 업로드 한 건.
 * <p>
 * {@code uploadUrl}과 {@code fileUrl}의 호스트가 다르다. 업로드는 S3로 직접 보내고(서명이 그
 * 호스트에 묶여 있다), 조회는 CloudFront를 거친다. 저장해야 하는 값은 {@code fileUrl} 쪽이다.
 *
 * @param key       버킷 안의 객체 키
 * @param uploadUrl 서명된 PUT 주소. 이 주소로 보낼 때 Content-Type과 크기가 발급 요청값과 정확히
 *                  같아야 한다, 다르면 S3가 서명 검증에서 거부한다
 * @param fileUrl   업로드 후 조회할 주소
 * @param expiresAt {@code uploadUrl}이 만료되는 시각
 */
public record PresignedUpload(
        String key,
        String uploadUrl,
        String fileUrl,
        LocalDateTime expiresAt
) {
}
