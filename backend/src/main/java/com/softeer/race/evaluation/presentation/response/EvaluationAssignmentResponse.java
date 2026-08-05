package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationAssignmentInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 배정 확정 결과.
 * <p>
 * 저장소의 다른 응답과 달리 연락처가 들어 있다. 방문 전에 판매자와 연락해야 하는 사람은 배정된
 * 평가사 한 명이고, 배정이 확정된 시점에 그 자격이 생긴다.
 */
@Schema(description = "평가사 배정 응답")
public record EvaluationAssignmentResponse(

        @Schema(description = "배정된 방문견적 신청 ID", example = "1")
        Long evaluationId,

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "방문 날짜", example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문 주소", example = "서울 성동구 왕십리로 83")
        String visitAddress,

        @Schema(description = "방문 시 연락받을 번호. 배정된 평가사에게만 나간다",
                example = "01012345678")
        String contactPhone,

        @Schema(description = "신청 상태. 배정으로 바뀌지 않아 평가가 끝날 때까지 REQUESTED다",
                example = "REQUESTED")
        String status
) {

    public static EvaluationAssignmentResponse from(EvaluationAssignmentInfo info) {
        return new EvaluationAssignmentResponse(
                info.evaluationId(),
                info.plateNumber(),
                info.visitDate(),
                info.visitAddress(),
                info.contactPhone(),
                info.status());
    }
}
