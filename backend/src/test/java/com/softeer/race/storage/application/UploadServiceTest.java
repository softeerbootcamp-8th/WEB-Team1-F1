package com.softeer.race.storage.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.application.dto.command.UploadCommand;
import com.softeer.race.storage.application.dto.info.UploadInfo;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.storage.domain.PresignedUpload;
import com.softeer.race.storage.domain.UploadContentType;
import com.softeer.race.storage.exception.StorageErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * 시나리오
 * <ol>
 *   <li>요청한 건수만큼 발급하고 요청 순서를 유지한다</li>
 *   <li>MIME 타입의 대소문자를 가리지 않는다</li>
 *   <li>PDF도 이미지와 같은 경로로 발급한다</li>
 *   <li>지원하지 않는 형식은 UNSUPPORTED_TYPE</li>
 *   <li>형식별 크기 상한을 넘으면 FILE_TOO_LARGE</li>
 *   <li>이미지 상한을 넘는 크기라도 PDF면 통과한다</li>
 *   <li>한 건이라도 잘못되면 아무것도 발급하지 않는다</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("업로드 주소 발급 서비스")
class UploadServiceTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 2, 15, 30);

    private static final long OVER_IMAGE_LIMIT = 10L * 1024 * 1024 + 1;
    private static final long OVER_DOCUMENT_LIMIT = 20L * 1024 * 1024 + 1;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private UploadService uploadService;

    @Test
    @DisplayName("요청한 건수만큼 발급하고 요청 순서를 그대로 유지한다")
    void issue() {
        // given : 형식이 서로 다른 세 건
        given(fileStorage.presign(UploadContentType.JPEG, 100L)).willReturn(upload("first.jpg"));
        given(fileStorage.presign(UploadContentType.PNG, 200L)).willReturn(upload("second.png"));
        given(fileStorage.presign(UploadContentType.WEBP, 300L)).willReturn(upload("third.webp"));

        UploadCommand command = command(
                file("image/jpeg", 100L), file("image/png", 200L), file("image/webp", 300L));

        // when
        UploadInfo info = uploadService.issue(command);

        // then : 순서가 뒤바뀌면 클라이언트가 어느 주소가 어느 파일 것인지 알 수 없다
        assertThat(info.uploads())
                .extracting(PresignedUpload::key)
                .containsExactly("first.jpg", "second.png", "third.webp");
    }

    @Test
    @DisplayName("MIME 타입의 대소문자를 가리지 않는다")
    void issueIgnoresCase() {
        // given
        given(fileStorage.presign(UploadContentType.JPEG, 100L)).willReturn(upload("first.jpg"));

        // when
        UploadInfo info = uploadService.issue(command(file("IMAGE/JPEG", 100L)));

        // then
        assertThat(info.uploads()).hasSize(1);
    }

    @Test
    @DisplayName("PDF도 이미지와 같은 요청으로 함께 발급한다")
    void issueDocumentWithImage() {
        // given : 평가사가 현장 사진과 진단서를 한 번에 올리는 경우다
        given(fileStorage.presign(UploadContentType.JPEG, 100L)).willReturn(upload("photo.jpg"));
        given(fileStorage.presign(UploadContentType.PDF, 200L)).willReturn(upload("report.pdf"));

        // when
        UploadInfo info = uploadService.issue(
                command(file("image/jpeg", 100L), file("application/pdf", 200L)));

        // then
        assertThat(info.uploads())
                .extracting(PresignedUpload::key)
                .containsExactly("photo.jpg", "report.pdf");
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 UNSUPPORTED_TYPE")
    void issueRejectsUnsupportedType() {
        // given
        UploadCommand command = command(file("image/gif", 100L));

        // when & then
        assertThatThrownBy(() -> uploadService.issue(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StorageErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    @DisplayName("이미지가 10MB를 넘으면 FILE_TOO_LARGE")
    void issueRejectsOversizedImage() {
        // given : 요청 검증의 절대 상한(20MB)은 통과하지만 이미지 상한은 넘는 크기다.
        //         상한이 형식마다 달라 형식을 알아낸 뒤에야 판정할 수 있다
        UploadCommand command = command(file("image/jpeg", OVER_IMAGE_LIMIT));

        // when & then
        assertThatThrownBy(() -> uploadService.issue(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StorageErrorCode.FILE_TOO_LARGE);

        then(fileStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미지 상한을 넘는 크기라도 PDF면 통과한다")
    void issueAllowsLargeDocument() {
        // given : 스캔 진단서는 사진보다 크다. 형식별 상한이 없으면 정상적인 진단서가 거부된다
        given(fileStorage.presign(UploadContentType.PDF, OVER_IMAGE_LIMIT))
                .willReturn(upload("report.pdf"));

        // when
        UploadInfo info = uploadService.issue(command(file("application/pdf", OVER_IMAGE_LIMIT)));

        // then
        assertThat(info.uploads()).hasSize(1);
    }

    @Test
    @DisplayName("PDF도 20MB를 넘으면 FILE_TOO_LARGE")
    void issueRejectsOversizedDocument() {
        // given
        UploadCommand command = command(file("application/pdf", OVER_DOCUMENT_LIMIT));

        // when & then
        assertThatThrownBy(() -> uploadService.issue(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StorageErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("한 건이라도 잘못되면 앞선 건도 발급하지 않는다")
    void issueDoesNotPartiallyIssue() {
        // given : 첫 건은 정상, 두 번째가 잘못된 형식이다
        UploadCommand command = command(
                file("image/jpeg", 100L), file("image/gif", 200L));

        // when
        assertThatThrownBy(() -> uploadService.issue(command))
                .isInstanceOf(BusinessException.class);

        // then : 한 건씩 검증하며 발급하면 첫 건은 이미 발급된 채로 400이 나간다
        //        클라이언트는 그 주소를 받지 못하므로 아무도 쓰지 않는 자리만 남는다
        then(fileStorage).shouldHaveNoInteractions();
    }

    private static UploadCommand command(UploadCommand.UploadFile... files) {
        return new UploadCommand(List.of(files));
    }

    private static UploadCommand.UploadFile file(String contentType, long contentLength) {
        return new UploadCommand.UploadFile(contentType, contentLength);
    }

    private static PresignedUpload upload(String key) {
        return new PresignedUpload(key, "https://s3/" + key, "https://cdn/" + key, EXPIRES_AT);
    }
}
