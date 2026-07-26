package com.softeer.race.common.presentation.dto.response;

public record ValidationErrorResponse(
        String field,
        String message
) {
}
