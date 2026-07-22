package com.softeer.race.sample.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "샘플 인사 응답")
public record SampleResponse(
        @Schema(description = "인사 메시지", example = "Hello, race!")
        String message
) {
}
