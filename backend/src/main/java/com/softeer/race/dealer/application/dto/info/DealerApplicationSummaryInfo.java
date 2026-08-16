package com.softeer.race.dealer.application.dto.info;

import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.user.domain.User;
import java.time.LocalDateTime;

/**
 * 관리자 심사 목록의 한 줄. 사원증은 담지 않는다 — 목록에서 스무 건을 서명하는데 관리자가 실제로
 * 열어 보는 것은 그중 한 건이고, 서명된 주소는 발급하는 만큼 새어 나갈 자리가 는다.
 */
public record DealerApplicationSummaryInfo(
        Long id,
        Long applicantId,
        String username,
        String realName,
        DealerApplicationStatus status,
        LocalDateTime appliedAt
) {

    public static DealerApplicationSummaryInfo from(DealerApplication application) {
        User applicant = application.getApplicant();

        return new DealerApplicationSummaryInfo(
                application.getId(),
                applicant.getId(),
                applicant.getUsername(),
                applicant.getRealName(),
                application.getStatus(),
                application.getCreatedAt());
    }
}
