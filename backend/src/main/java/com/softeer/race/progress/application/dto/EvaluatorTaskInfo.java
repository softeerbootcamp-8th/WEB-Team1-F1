package com.softeer.race.progress.application.dto;

import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.progress.domain.EvaluatorTaskGroup;
import com.softeer.race.progress.domain.EvaluatorTaskRow;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EvaluatorTaskInfo(
        Long evaluationId,
        EvaluatorTaskGroup group,
        EvaluationStatus status,
        LocalDate visitDate,
        String visitAddress,
        LocalDateTime requestedAt,

        Long vehicleId,
        Manufacturer manufacturer,
        String model,
        Integer modelYear,
        String plateNumber,
        String sellerName
) {

    public static EvaluatorTaskInfo from(EvaluatorTaskRow row) {
        return new EvaluatorTaskInfo(
                row.evaluationId(),
                row.group(),
                row.status(),
                row.visitDate(),
                row.visitAddress(),
                row.requestedAt(),
                row.vehicleId(),
                row.manufacturer(),
                row.model(),
                row.modelYear(),
                row.plateNumber(),
                row.sellerName());
    }
}
