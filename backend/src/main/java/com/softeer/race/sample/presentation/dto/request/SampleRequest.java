package com.softeer.race.sample.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "샘플 인사 요청")
public record SampleRequest(
        @Schema(description = "인사할 대상 이름", example = "race")
        String name
) {
}
