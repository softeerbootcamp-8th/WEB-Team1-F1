package com.softeer.race.dealer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.dealer.application.dto.command.RejectDealerApplicationCommand;
import com.softeer.race.dealer.application.dto.info.DealerApplicationDetailInfo;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.application.dto.info.DealerApplicationSummaryInfo;
import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationRepository;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.dealer.exception.DealerApplicationErrorCode;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import com.softeer.race.storage.domain.PresignedDealerLicenseView;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 딜러 심사")
class DealerApplicationReviewServiceTest {

    private static final long APPLICATION_ID = 1L;
    private static final String LICENSE_KEY =
            "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    @Mock
    private DealerApplicationRepository dealerApplicationRepository;

    @Mock
    private DealerLicenseStorage dealerLicenseStorage;

    private DealerApplicationReviewService dealerApplicationReviewService;

    @BeforeEach
    void setUp() {
        dealerApplicationReviewService = new DealerApplicationReviewService(
                dealerApplicationRepository, dealerLicenseStorage);
    }

    @Test
    @DisplayName("승인하면 신청자가 딜러가 된다")
    void approveGrantsDealerRole() {
        User applicant = applicant();
        givenLocked(DealerApplication.apply(applicant, LICENSE_KEY));

        DealerApplicationInfo info = dealerApplicationReviewService.approve(APPLICATION_ID);

        assertThat(info.status()).isEqualTo(DealerApplicationStatus.APPROVED);
        assertThat(applicant.getRole()).isEqualTo(Role.DEALER);
    }

    @Test
    @DisplayName("반려하면 사유가 남고 신청자는 일반 회원으로 남는다")
    void rejectKeepsGeneralRole() {
        User applicant = applicant();
        givenLocked(DealerApplication.apply(applicant, LICENSE_KEY));

        DealerApplicationInfo info = dealerApplicationReviewService.reject(
                new RejectDealerApplicationCommand(APPLICATION_ID, "사원증 사진이 흐립니다."));

        assertThat(info.status()).isEqualTo(DealerApplicationStatus.REJECTED);
        assertThat(info.rejectReason()).isEqualTo("사원증 사진이 흐립니다.");
        assertThat(applicant.getRole()).isEqualTo(Role.GENERAL);
    }

    // 관리자 둘이 같은 신청을 동시에 열었을 때 나중 판정이 앞 판정을 덮지 않아야 한다
    @Test
    @DisplayName("이미 판정된 신청은 다시 승인할 수 없다")
    void approveRejectsDecidedApplication() {
        DealerApplication application = DealerApplication.apply(applicant(), LICENSE_KEY);
        application.reject("이미 반려됨");
        givenLocked(application);

        assertThatThrownBy(() -> dealerApplicationReviewService.approve(APPLICATION_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.ALREADY_DECIDED));
    }

    @Test
    @DisplayName("없는 신청을 판정하려 하면 404다")
    void approveRejectsMissingApplication() {
        when(dealerApplicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealerApplicationReviewService.approve(APPLICATION_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("상세는 사원증을 볼 임시 주소를 함께 준다")
    void findDetailPresignsLicense() {
        when(dealerApplicationRepository.findDetailById(APPLICATION_ID))
                .thenReturn(Optional.of(DealerApplication.apply(applicant(), LICENSE_KEY)));
        when(dealerLicenseStorage.presignDealerLicenseView(LICENSE_KEY))
                .thenReturn(new PresignedDealerLicenseView(
                        "https://s3.example/signed", LocalDateTime.of(2026, 8, 16, 15, 19, 5)));

        DealerApplicationDetailInfo info = dealerApplicationReviewService.findDetail(APPLICATION_ID);

        assertThat(info.licenseViewUrl()).isEqualTo("https://s3.example/signed");
        assertThat(info.username()).isEqualTo("race_kim");
    }

    // 목록에서 스무 건을 서명해도 관리자가 여는 것은 한 건이다
    @Test
    @DisplayName("목록은 사원증 주소를 발급하지 않는다")
    void findAllDoesNotPresign() {
        when(dealerApplicationRepository.findAllByStatus(DealerApplicationStatus.PENDING))
                .thenReturn(List.of(DealerApplication.apply(applicant(), LICENSE_KEY)));

        List<DealerApplicationSummaryInfo> summaries =
                dealerApplicationReviewService.findAllByStatus(DealerApplicationStatus.PENDING);

        assertThat(summaries).singleElement()
                .satisfies(summary -> assertThat(summary.realName()).isEqualTo("김레이스"));
        verify(dealerLicenseStorage, never()).presignDealerLicenseView(LICENSE_KEY);
    }

    private void givenLocked(DealerApplication application) {
        when(dealerApplicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application));
    }

    private static User applicant() {
        return User.create("race_kim", "race@race.kr", "$2a$10$encoded",
                "김레이스", "01012345678", Role.GENERAL);
    }
}
