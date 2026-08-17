package com.softeer.race.dealer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.dealer.exception.DealerApplicationErrorCode;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("딜러 심사 신청 상태 전이")
class DealerApplicationTest {

    private static final String LICENSE_KEY =
            "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    // 승인과 역할 승격이 갈라지면 "승인됐는데 아직 일반 회원"인 행이 생긴다
    @Test
    @DisplayName("승인은 신청자에게 딜러 자격까지 붙인다")
    void approveGrantsDealerRole() {
        User applicant = applicant();
        DealerApplication application = DealerApplication.apply(applicant, LICENSE_KEY);

        application.approve();

        assertThat(application.getStatus()).isEqualTo(DealerApplicationStatus.APPROVED);
        assertThat(applicant.getRole()).isEqualTo(Role.DEALER);
    }

    @Test
    @DisplayName("반려는 사유를 남기고 신청자를 일반 회원으로 둔다")
    void rejectKeepsGeneralRole() {
        User applicant = applicant();
        DealerApplication application = DealerApplication.apply(applicant, LICENSE_KEY);

        application.reject("사원증 사진이 흐려 확인할 수 없습니다.");

        assertThat(application.getStatus()).isEqualTo(DealerApplicationStatus.REJECTED);
        assertThat(application.getRejectReason()).isEqualTo("사원증 사진이 흐려 확인할 수 없습니다.");
        assertThat(applicant.getRole()).isEqualTo(Role.GENERAL);
    }

    @Test
    @DisplayName("대기 중인 신청은 판정할 수 있다")
    void validateDecidablePassesWhilePending() {
        DealerApplication application = DealerApplication.apply(applicant(), LICENSE_KEY);

        application.validateDecidable();
    }

    // 승인이 반려를 덮으면 반려 사유가 남은 채 딜러가 되고, 반대면 이미 승격된 역할이 남는다
    @Test
    @DisplayName("이미 판정된 신청은 다시 판정할 수 없다")
    void validateDecidableRejectsDecided() {
        DealerApplication approved = DealerApplication.apply(applicant(), LICENSE_KEY);
        approved.approve();

        assertThatThrownBy(approved::validateDecidable)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.ALREADY_DECIDED));

        DealerApplication rejected = DealerApplication.apply(applicant(), LICENSE_KEY);
        rejected.reject("사유");

        assertThatThrownBy(rejected::validateDecidable)
                .isInstanceOf(BusinessException.class);
    }

    private static User applicant() {
        return User.create("race_kim", "race@race.kr", "$2a$10$encoded",
                "김레이스", "01012345678", Role.GENERAL);
    }
}
