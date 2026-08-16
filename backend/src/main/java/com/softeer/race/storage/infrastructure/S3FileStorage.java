package com.softeer.race.storage.infrastructure;

import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import com.softeer.race.storage.domain.PresignedUpload;
import com.softeer.race.storage.domain.PresignedDealerLicense;
import com.softeer.race.storage.domain.PresignedDealerLicenseView;
import com.softeer.race.storage.domain.UploadContentType;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.exception.StorageErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * S3에 직접 올릴 수 있는 서명된 PUT 주소를 발급한다.
 */
@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage, DealerLicenseStorage {

    private static final DateTimeFormatter KEY_DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * 종류별로 {@link #createKey}가 만드는 키의 형태를 그대로 옮긴 것. 접두사, 날짜 두 칸, UUID
     * 파일명, 그 종류에 허용된 확장자 순이다. 접두사와 확장자 목록을 {@link FileCategory}와
     * {@link UploadContentType}에서 끌어오므로 형식을 하나 추가해도 발급 규칙과 판정 규칙이
     * 갈라지지 않는다.
     * <p>
     * <b>종류마다 패턴을 따로 만드는 것이 핵심이다.</b> 확장자를 한 목록으로 합쳐 하나의 패턴으로
     * 두면 {@code documents/…/x.pdf}가 이미지 판정도 통과해, 진단서를 차량 사진으로 등록할 수 있다.
     * <p>
     * 각 칸을 발급 결과가 낼 수 있는 값까지 좁힌다. 월은 {@code LocalDate}가 {@code 01}~{@code 12}만
     * 내고 UUID의 hex는 {@link UUID#toString()}이 소문자만 낸다. {@code \d{2}}나 대소문자 혼용을
     * 허용하면 <b>발급한 적 없는 키가 통과하는 만큼만 넓어지고 얻는 것이 없다.</b>
     */
    private static final Map<FileCategory, Pattern> MANAGED_KEY_PATTERNS =
            Arrays.stream(FileCategory.values())
                    .collect(Collectors.toUnmodifiableMap(
                            category -> category, S3FileStorage::managedKeyPattern));

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final Clock clock;

    @Override
    public PresignedUpload presign(UploadContentType contentType, long contentLength) {
        String key = createKey(contentType.category(), contentType);

        PresignedPutObjectRequest presigned = presignPut(key, contentType, contentLength);

        // 업로드 주소는 서명이 묶인 S3 호스트, 조회 주소는 CloudFront다. 저장할 값은 뒤쪽이다
        return new PresignedUpload(
                key,
                presigned.url().toString(),
                s3Properties.cdnBaseUrl() + "/" + key,
                LocalDateTime.ofInstant(presigned.expiration(), clock.getZone()));
    }

    @Override
    public PresignedDealerLicense presignDealerLicense(
            UploadContentType contentType, long contentLength) {
        String key = createKey(FileCategory.DEALER_LICENSE, contentType);
        PresignedPutObjectRequest presigned = presignPut(key, contentType, contentLength);

        return new PresignedDealerLicense(
                key,
                presigned.url().toString(),
                LocalDateTime.ofInstant(presigned.expiration(), clock.getZone()));
    }

    /**
     * 키 형태를 여기서 다시 확인한다. 호출자가 DB에서 읽어 온 값이라 믿을 만해 보이지만, 그 값이
     * 어디서 왔든 <b>서명해 주는 순간 그 객체를 읽을 수 있는 주소가 된다.</b> 형태를 좁혀 두면
     * 우리가 발급한 적 없는 키에는 서명이 나가지 않는다.
     * <p>
     * 객체가 실제로 있는지는 확인하지 않는다. HeadObject 를 한 번 더 부르는 비용을 치러도
     * 서명과 조회 사이에 지워지면 어차피 404라, 그 판정은 브라우저에 맡긴다.
     */
    @Override
    public PresignedDealerLicenseView presignDealerLicenseView(String key) {
        if (key == null
                || !MANAGED_KEY_PATTERNS.get(FileCategory.DEALER_LICENSE).matcher(key).matches()) {
            throw new BusinessException(StorageErrorCode.INVALID_DEALER_LICENSE_KEY);
        }

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(s3Properties.presignExpiry())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(s3Properties.bucket())
                                .key(key)
                                .build())
                        .build());

        return new PresignedDealerLicenseView(
                presigned.url().toString(),
                LocalDateTime.ofInstant(presigned.expiration(), clock.getZone()));
    }

    private PresignedPutObjectRequest presignPut(
            String key, UploadContentType contentType, long contentLength) {

        // Content-Type과 크기를 요청에 넣으면 둘 다 서명에 포함된다. 클라이언트가 신고한 값과 실제
        // 업로드가 다르면 S3가 서명 검증에서 거부하므로, 파일을 받지 않고도 형식과 크기는 강제된다
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(contentType.mimeType())
                .contentLength(contentLength)
                .build();

        return s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(s3Properties.presignExpiry())
                        .putObjectRequest(objectRequest)
                        .build());
    }

    @Override
    public boolean isValidUploadedDealerLicense(String key) {
        if (key == null || !MANAGED_KEY_PATTERNS.get(FileCategory.DEALER_LICENSE).matcher(key).matches()) {
            return false;
        }

        try {
            HeadObjectResponse object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());

            UploadContentType contentType;
            try {
                contentType = UploadContentType.from(object.contentType());
            } catch (BusinessException exception) {
                return false;
            }

            return contentType.isDealerLicenseAllowed()
                    && key.endsWith("." + contentType.extension())
                    && object.contentLength() != null
                    && object.contentLength() > 0
                    && object.contentLength() <= UploadContentType.MAX_DEALER_LICENSE_SIZE;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new BusinessException(StorageErrorCode.STORAGE_UNAVAILABLE);
        } catch (SdkException exception) {
            throw new BusinessException(StorageErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    /**
     * 접두사만 확인하면 <b>발급하지 않은 키가 통과한다.</b> {@code .../images/../other} 처럼 상위
     * 경로를 타거나, 그걸 {@code %2e%2e}로 퍼센트 인코딩해 문자열 검사만 피해 가는 값이 있다.
     * S3는 키를 리터럴로 다뤄 {@code ..}를 경로 이동으로 풀지 않으므로 실제로 버킷 밖을 가리키지는
     * 않지만, 우리가 서명해 준 적 없는 객체를 차량 이미지로 박아 넣는 것은 그대로 된다.
     * <p>
     * 그래서 걸러낼 문자열을 나열하는 대신 <b>그 종류로 발급한 키 형태와 정확히 일치하는지</b> 본다.
     * 쿼리나 프래그먼트가 붙은 값, 그리고 다른 종류로 발급된 우리 주소가 이 규칙에서 함께 떨어진다.
     */
    @Override
    public boolean isManagedUrl(String fileUrl, FileCategory category) {
        // 사원증은 공개 조회 URL이라는 개념 자체가 없다. 전용 키와 HeadObject로만 검증한다.
        if (fileUrl == null || category == FileCategory.DEALER_LICENSE) {
            return false;
        }
        String baseUrl = s3Properties.cdnBaseUrl() + "/";
        if (!fileUrl.startsWith(baseUrl)) {
            return false;
        }
        return MANAGED_KEY_PATTERNS.get(category)
                .matcher(fileUrl.substring(baseUrl.length()))
                .matches();
    }

    private static Pattern managedKeyPattern(FileCategory category) {
        return Pattern.compile(
                "%s/\\d{4}/(?:0[1-9]|1[0-2])/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:%s)"
                        .formatted(category.keyPrefix(), allowedExtensions(category)));
    }

    private static String allowedExtensions(FileCategory category) {
        if (category == FileCategory.DEALER_LICENSE) {
            return Arrays.stream(UploadContentType.values())
                    .filter(UploadContentType::isDealerLicenseAllowed)
                    .map(UploadContentType::extension)
                    .collect(Collectors.joining("|"));
        }
        return Arrays.stream(UploadContentType.values())
                .filter(type -> type.category() == category)
                .map(UploadContentType::extension)
                .collect(Collectors.joining("|"));
    }

    /**
     * 키에 용도나 대상 식별자를 넣지 않는다. 어느 파일이 무엇에 붙은 것인지는 DB가 알고 있고,
     * 키에까지 담으려면 업로드 시점에 그 대상이 이미 있어야 하거나 나중에 객체를 옮겨야 한다.
     * S3에는 이동이 없어 복사 후 삭제가 되는데, 그러면 <b>이미 발급해 화면이 쓰고 있던 주소가
     * 바뀐다.</b>
     * <p>
     * 다만 <b>종류는 접두사로 넣는다.</b> 그건 대상 식별자와 달리 업로드 시점에 이미 확정돼 있고
     * (형식이 곧 종류다) 나중에 바뀌지 않는다. 버킷을 프론트 정적 파일과 함께 쓰므로 이 접두사들
     * 아래로 격리하는 효과도 있다.
     * <p>
     * 날짜로 나누는 것은 한 접두사 아래 객체가 무한정 쌓이지 않게 하려는 것뿐이다. 파일명은 UUID라
     * 같은 이름이 겹쳐 덮어써질 일이 없다.
     */
    private String createKey(FileCategory category, UploadContentType contentType) {
        return "%s/%s/%s.%s".formatted(
                category.keyPrefix(),
                LocalDate.now(clock).format(KEY_DATE_PATTERN),
                UUID.randomUUID(),
                contentType.extension());
    }
}
