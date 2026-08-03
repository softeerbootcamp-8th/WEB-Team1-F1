package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.command.ImageUploadCommand;
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
 * 한 번에 여러 건을 받는 이유는 평가사가 부위별로 사진을 여러 장 올리기 때문이다. 장당 한 번씩
 * 발급받으면 사진 열 장에 왕복이 열 번이다.
 * <p>
 * <b>파일명을 받지 않는다.</b> 확장자는 서버가 {@code contentType}에서 정한다. 파일명을 받으면
 * 경로 구분자나 이중 확장자가 섞인 값을 걸러내야 하는데, 받지 않으면 그 문제가 아예 없다.
 */
@Schema(description = "평가 사진 업로드 주소 발급 요청")
public record ImageUploadRequest(

        @Schema(description = "발급받을 파일 목록")
        @NotEmpty(message = "발급받을 파일 정보가 최소 한 건은 필요합니다.")
        @Size(max = ImageUploadRequest.MAX_FILE_COUNT,
                message = "한 번에 " + ImageUploadRequest.MAX_FILE_COUNT + "건까지 발급받을 수 있습니다.")
        List<@Valid ImageFileRequest> files
) {

    static final int MAX_FILE_COUNT = 20;

    /**
     * 현장에서 찍은 폰 사진 원본이 장당 3~8MB라 그보다 여유 있게 잡는다.
     */
    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /**
     * 인증 주체를 넘기지 않는다. 발급에 사용자 정보가 필요하지 않고 키에도 들어가지 않는다.
     * 로그인을 요구하는 것은 아무나 서명된 주소를 받아 가지 못하게 하려는 것뿐이다.
     */
    public ImageUploadCommand toCommand() {
        return new ImageUploadCommand(files.stream()
                .map(file -> new ImageUploadCommand.ImageFile(file.contentType(), file.contentLength()))
                .toList());
    }

    @Schema(description = "업로드할 파일 정보")
    public record ImageFileRequest(

            @Schema(description = "파일의 MIME 타입", example = "image/jpeg",
                    allowableValues = {"image/jpeg", "image/png", "image/webp"})
            @NotBlank(message = "contentType은 필수입니다.")
            String contentType,

            /*
             * 실제로 업로드할 파일의 정확한 바이트 수여야 한다. 이 값이 서명에 들어가므로 여기 적은
             * 크기와 다른 파일을 보내면 S3가 거부한다.
             */
            @Schema(description = "파일 크기(바이트). 실제 업로드할 파일과 정확히 같아야 합니다.",
                    example = "2481920")
            @Positive(message = "contentLength는 0보다 커야 합니다.")
            @Max(value = ImageUploadRequest.MAX_FILE_SIZE,
                    message = "파일 크기는 10MB를 넘을 수 없습니다.")
            long contentLength
    ) {
    }
}
