package com.softeer.race.dealer.application.dto.info;

import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import java.time.LocalDateTime;

/**
 * 신청자가 보는 자기 신청. 사원증 키를 담지 않는다 — 신청자는 자기가 올린 파일을 이미 알고,
 * 비공개 객체 키가 응답으로 나가면 그 키를 아는 사람이 늘어난다.
 */
public record DealerApplicationInfo(
        Long id,
        DealerApplicationStatus status,
        String rejectReason,
        LocalDateTime appliedAt
) {

    public static DealerApplicationInfo from(DealerApplication application) {
        return new DealerApplicationInfo(
                application.getId(),
                application.getStatus(),
                application.getRejectReason(),
                application.getCreatedAt());
    }
}
