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
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    /**
     * {@link #createKey}가 만드는 키의 형태를 그대로 옮긴 것. 날짜 두 칸, UUID 파일명, 허용 확장자
     * 순이다. 확장자 목록은 {@link ImageContentType}에서 끌어오므로 형식을 하나 추가해도 발급
     * 규칙과 판정 규칙이 갈라지지 않는다.
     */
    private static final Pattern MANAGED_KEY_PATTERN = Pattern.compile(
            "%s/\\d{4}/\\d{2}/[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\.(?:%s)"
                    .formatted(KEY_PREFIX, allowedExtensions()));

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

    /**
     * 접두사만 확인하면 <b>발급하지 않은 키가 통과한다.</b> {@code .../images/../other} 처럼 상위
     * 경로를 타거나, 그걸 {@code %2e%2e}로 퍼센트 인코딩해 문자열 검사만 피해 가는 값이 있다.
     * S3는 키를 리터럴로 다뤄 {@code ..}를 경로 이동으로 풀지 않으므로 실제로 버킷 밖을 가리키지는
     * 않지만, 우리가 서명해 준 적 없는 객체를 차량 이미지로 박아 넣는 것은 그대로 된다.
     * <p>
     * 그래서 걸러낼 문자열을 나열하는 대신 <b>발급한 키 형태와 정확히 일치하는지</b> 본다. 쿼리나
     * 프래그먼트가 붙은 값도 이 규칙에서 함께 떨어진다.
     */
    @Override
    public boolean isManagedUrl(String fileUrl) {
        if (fileUrl == null) {
            return false;
        }
        String baseUrl = s3Properties.cdnBaseUrl() + "/";
        if (!fileUrl.startsWith(baseUrl)) {
            return false;
        }
        return MANAGED_KEY_PATTERN.matcher(fileUrl.substring(baseUrl.length())).matches();
    }

    private static String allowedExtensions() {
        return Arrays.stream(ImageContentType.values())
                .map(ImageContentType::extension)
                .collect(Collectors.joining("|"));
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
