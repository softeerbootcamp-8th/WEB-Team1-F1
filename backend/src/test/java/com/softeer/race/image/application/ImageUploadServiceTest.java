package com.softeer.race.image.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.image.application.dto.command.ImageUploadCommand;
import com.softeer.race.image.application.dto.info.ImageUploadInfo;
import com.softeer.race.image.domain.ImageContentType;
import com.softeer.race.image.domain.ImageStorage;
import com.softeer.race.image.domain.PresignedUpload;
import com.softeer.race.image.exception.ImageErrorCode;
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
 *   <li>지원하지 않는 형식은 UNSUPPORTED_TYPE</li>
 *   <li>한 건이라도 형식이 잘못되면 아무것도 발급하지 않는다</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("평가 사진 업로드 서비스")
class ImageUploadServiceTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 2, 15, 30);

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private ImageUploadService evaluationImageService;

    @Test
    @DisplayName("요청한 건수만큼 발급하고 요청 순서를 그대로 유지한다")
    void issue() {
        // given : 형식이 서로 다른 세 건
        given(imageStorage.presign(ImageContentType.JPEG, 100L)).willReturn(upload("first.jpg"));
        given(imageStorage.presign(ImageContentType.PNG, 200L)).willReturn(upload("second.png"));
        given(imageStorage.presign(ImageContentType.WEBP, 300L)).willReturn(upload("third.webp"));

        ImageUploadCommand command = command(
                file("image/jpeg", 100L), file("image/png", 200L), file("image/webp", 300L));

        // when
        ImageUploadInfo info = evaluationImageService.issue(command);

        // then : 순서가 뒤바뀌면 클라이언트가 어느 주소가 어느 파일 것인지 알 수 없다
        assertThat(info.uploads())
                .extracting(PresignedUpload::key)
                .containsExactly("first.jpg", "second.png", "third.webp");
    }

    @Test
    @DisplayName("MIME 타입의 대소문자를 가리지 않는다")
    void issueIgnoresCase() {
        // given
        given(imageStorage.presign(ImageContentType.JPEG, 100L)).willReturn(upload("first.jpg"));

        // when
        ImageUploadInfo info = evaluationImageService.issue(command(file("IMAGE/JPEG", 100L)));

        // then
        assertThat(info.uploads()).hasSize(1);
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 UNSUPPORTED_TYPE")
    void issueRejectsUnsupportedType() {
        // given
        ImageUploadCommand command = command(file("application/pdf", 100L));

        // when & then
        assertThatThrownBy(() -> evaluationImageService.issue(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ImageErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    @DisplayName("한 건이라도 형식이 잘못되면 앞선 건도 발급하지 않는다")
    void issueDoesNotPartiallyIssue() {
        // given : 첫 건은 정상, 두 번째가 잘못된 형식이다
        ImageUploadCommand command = command(
                file("image/jpeg", 100L), file("application/pdf", 200L));

        // when
        assertThatThrownBy(() -> evaluationImageService.issue(command))
                .isInstanceOf(BusinessException.class);

        // then : 한 건씩 검증하며 발급하면 첫 건은 이미 발급된 채로 400이 나간다
        //        클라이언트는 그 주소를 받지 못하므로 아무도 쓰지 않는 자리만 남는다
        then(imageStorage).shouldHaveNoInteractions();
    }

    private static ImageUploadCommand command(ImageUploadCommand.ImageFile... files) {
        return new ImageUploadCommand(List.of(files));
    }

    private static ImageUploadCommand.ImageFile file(String contentType, long contentLength) {
        return new ImageUploadCommand.ImageFile(contentType, contentLength);
    }

    private static PresignedUpload upload(String key) {
        return new PresignedUpload(key, "https://s3/" + key, "https://cdn/" + key, EXPIRES_AT);
    }
}
