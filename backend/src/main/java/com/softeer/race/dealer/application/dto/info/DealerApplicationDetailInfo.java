package com.softeer.race.dealer.application.dto.info;

import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.storage.domain.PresignedDealerLicenseView;
import com.softeer.race.user.domain.User;
import java.time.LocalDateTime;

/**
 * 관리자가 판정하기 전에 보는 신청 상세. 신청자 정보와 사원증을 볼 임시 주소가 함께 온다.
 * <p>
 * 사원증 객체 키는 담지 않는다. 관리자가 보아야 하는 것은 이미지이지 키가 아니고, 키가 화면까지
 * 내려가면 서명 없이 그 값을 아는 사람이 는다.
 */
public record DealerApplicationDetailInfo(
        Long id,
        Long applicantId,
        String username,
        String realName,
        String email,
        String phone,
        DealerApplicationStatus status,
        String rejectReason,
        LocalDateTime appliedAt,
        String licenseViewUrl,
        // 이미지인지 PDF인지. 사원증은 둘 다 받으므로 화면이 무엇을 그릴지 이 값으로 가른다
        String licenseContentType,
        LocalDateTime licenseViewExpiresAt
) {

    public static DealerApplicationDetailInfo from(
            DealerApplication application, PresignedDealerLicenseView licenseView) {

        User applicant = application.getApplicant();

        return new DealerApplicationDetailInfo(
                application.getId(),
                applicant.getId(),
                applicant.getUsername(),
                applicant.getRealName(),
                applicant.getEmail(),
                applicant.getPhone(),
                application.getStatus(),
                application.getRejectReason(),
                application.getCreatedAt(),
                licenseView.viewUrl(),
                licenseView.contentType(),
                licenseView.expiresAt());
    }
}
