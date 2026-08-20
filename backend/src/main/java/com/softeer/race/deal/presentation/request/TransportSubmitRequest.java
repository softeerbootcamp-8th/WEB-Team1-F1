package com.softeer.race.deal.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 판매자가 내는 명의이전 서류와 탁송 출발 일정
 * <p>
 * 파일이 오지 않는다. 브라우저가 발급받은 주소로 S3 에 직접 올리고, 여기에는 그 결과 주소만 온다.
 * <p>
 * 미래인지는 여기서 보지 않는다. {@code @Future} 는 시스템 시각을 쓰는데 이 프로젝트의 시각
 * 판정은 주입된 Clock 이라, 두 기준이 섞이면 테스트에서만 통과하는 검증이 생긴다
 */
@Schema(description = "명의이전 서류·탁송 출발 일정 제출")
public record TransportSubmitRequest(

        @Schema(description = "업로드한 명의이전 서류 PDF 의 조회 주소")
        @NotBlank(message = "명의이전 서류를 첨부해 주세요.")
        String documentUrl,

        @Schema(description = "탁송 출발 일시", example = "2026-08-20T14:00:00")
        @NotNull(message = "탁송 출발 일시는 필수입니다.")
        LocalDateTime transportAt,

        @Schema(description = "탁송 출발지", example = "서울시 강남구 테헤란로 123")
        @NotBlank(message = "탁송 출발지는 필수입니다.")
        String transportLocation
) {
}
