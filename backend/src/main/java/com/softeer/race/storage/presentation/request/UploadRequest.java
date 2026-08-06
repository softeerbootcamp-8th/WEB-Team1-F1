package com.softeer.race.storage.presentation.request;

import com.softeer.race.storage.application.dto.command.UploadCommand;
import com.softeer.race.storage.domain.UploadContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 업로드 주소 발급 요청. 파일은 오지 않고 형식과 크기만 온다.
 * <p>
 * 한 번에 여러 건을 받는 이유는 부위별로 사진을 여러 장 올리기 때문이다. 장당 한 번씩
 * 발급받으면 사진 열 장에 왕복이 열 번이다.
 * <p>
 * <b>파일명을 받지 않는다.</b> 확장자는 서버가 {@code contentType}에서 정한다. 파일명을 받으면
 * 경로 구분자나 이중 확장자가 섞인 값을 걸러내야 하는데, 받지 않으면 그 문제가 아예 없다.
 */
@Schema(description = "업로드 주소 발급 요청")
public record UploadRequest(

        @Schema(description = "발급받을 파일 목록")
        @NotEmpty(message = "발급받을 파일 정보가 최소 한 건은 필요합니다.")
        @Size(max = UploadRequest.MAX_FILE_COUNT,
                message = "한 번에 " + UploadRequest.MAX_FILE_COUNT + "건까지 발급받을 수 있습니다.")
        List<@Valid UploadFileRequest> files
) {

    static final int MAX_FILE_COUNT = 20;

    /**
     * <b>형식별 상한이 아니라 절대 상한이다.</b> 가장 큰 형식(문서)의 상한을 그대로 쓰므로, 여기를
     * 통과했다고 그 형식으로 올릴 수 있다는 뜻은 아니다 — 15MB짜리 JPEG은 여기를 지나 서비스에서
     * {@code STORAGE_FILE_TOO_LARGE}로 떨어진다.
     * <p>
     * 그래도 이 검증을 남겨 두는 이유는 명백히 불가능한 크기를 형식과 무관하게 걸러내면서
     * {@code files[1].contentLength}로 어느 파일인지 짚어줄 수 있기 때문이다.
     */
    static final long MAX_FILE_SIZE = UploadContentType.MAX_DOCUMENT_SIZE;

    /**
     * 인증 주체를 넘기지 않는다. 발급에 사용자 정보가 필요하지 않고 키에도 들어가지 않는다.
     * 로그인을 요구하는 것은 아무나 서명된 주소를 받아 가지 못하게 하려는 것뿐이다.
     */
    public UploadCommand toCommand() {
        return new UploadCommand(files.stream()
                .map(file -> new UploadCommand.UploadFile(file.contentType(), file.contentLength()))
                .toList());
    }

    @Schema(description = "업로드할 파일 정보")
    public record UploadFileRequest(

            @Schema(description = "파일의 MIME 타입", example = "image/jpeg",
                    allowableValues = {"image/jpeg", "image/png", "image/webp", "application/pdf"})
            @NotBlank(message = "contentType은 필수입니다.")
            String contentType,

            /*
             * 실제로 업로드할 파일의 정확한 바이트 수여야 한다. 이 값이 서명에 들어가므로 여기 적은
             * 크기와 다른 파일을 보내면 S3가 거부한다.
             */
            @Schema(description = "파일 크기(바이트). 실제 업로드할 파일과 정확히 같아야 합니다. "
                    + "이미지는 10MB, 문서는 20MB까지 허용합니다.",
                    example = "2481920")
            @Positive(message = "contentLength는 0보다 커야 합니다.")
            @Max(value = UploadRequest.MAX_FILE_SIZE,
                    message = "파일 크기는 20MB를 넘을 수 없습니다.")
            long contentLength
    ) {
    }
}
