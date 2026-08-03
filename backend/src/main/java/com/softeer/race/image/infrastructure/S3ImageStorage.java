package com.softeer.race.image.infrastructure;

import com.softeer.race.image.domain.ImageContentType;
import com.softeer.race.image.domain.ImageStorage;
import com.softeer.race.image.domain.PresignedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * S3에 직접 올릴 수 있는 서명된 PUT 주소를 발급한다.
 */
@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    /**
     * 버킷을 프론트 정적 파일과 함께 쓰므로 이 아래로 격리한다. 키를 만들 때와 관리 대상인지
     * 판정할 때 같은 값을 쓴다 — 두 규칙이 갈라지면 방금 발급한 주소가 검증에서 떨어진다.
     */
    private static final String KEY_PREFIX = "images";

    private static final DateTimeFormatter KEY_DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy/MM");

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final Clock clock;

    @Override
    public PresignedUpload presign(ImageContentType contentType, long contentLength) {
        String key = createKey(contentType);

        // Content-Type과 크기를 요청에 넣으면 둘 다 서명에 포함된다. 클라이언트가 신고한 값과 실제
        // 업로드가 다르면 S3가 서명 검증에서 거부하므로, 파일을 받지 않고도 형식과 크기는 강제된다
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(contentType.mimeType())
                .contentLength(contentLength)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(s3Properties.presignExpiry())
                        .putObjectRequest(objectRequest)
                        .build());

        // 업로드 주소는 서명이 묶인 S3 호스트, 조회 주소는 CloudFront다. 저장할 값은 뒤쪽이다
        return new PresignedUpload(
                key,
                presigned.url().toString(),
                s3Properties.cdnBaseUrl() + "/" + key,
                LocalDateTime.ofInstant(presigned.expiration(), clock.getZone()));
    }

    @Override
    public boolean isManagedUrl(String fileUrl) {
        if (fileUrl == null) {
            return false;
        }
        // 상위 경로 표기를 걸러낸다. startsWith 만으로는 .../images/../ 로 접두사를 통과한 뒤
        // 전혀 다른 곳을 가리키는 주소를 막지 못한다
        if (fileUrl.contains("..")) {
            return false;
        }
        return fileUrl.startsWith(s3Properties.cdnBaseUrl() + "/" + KEY_PREFIX + "/");
    }

    /**
     * 키에 용도나 대상 식별자를 넣지 않는다. 어느 사진이 무엇에 붙은 것인지는 DB가 알고 있고,
     * 키에까지 담으려면 업로드 시점에 그 대상이 이미 있어야 하거나 나중에 객체를 옮겨야 한다.
     * S3에는 이동이 없어 복사 후 삭제가 되는데, 그러면 <b>이미 발급해 화면이 쓰고 있던 주소가
     * 바뀐다.</b>
     * <p>
     * 날짜로 나누는 것은 한 접두사 아래 객체가 무한정 쌓이지 않게 하려는 것뿐이다. 파일명은 UUID라
     * 같은 이름이 겹쳐 덮어써질 일이 없다.
     */
    private String createKey(ImageContentType contentType) {
        return "%s/%s/%s.%s".formatted(
                KEY_PREFIX,
                LocalDate.now(clock).format(KEY_DATE_PATTERN),
                UUID.randomUUID(),
                contentType.extension());
    }
}
