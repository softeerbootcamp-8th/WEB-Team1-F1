package com.softeer.race.dealer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationRepository;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.dealer.exception.DealerApplicationErrorCode;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("딜러 심사 신청")
class DealerApplicationServiceTest {

    private static final long APPLICANT_ID = 1L;
    private static final String LICENSE_KEY =
            "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    @Mock
    private DealerApplicationRepository dealerApplicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DealerLicenseStorage dealerLicenseStorage;

    private DealerApplicationService dealerApplicationService;

    @BeforeEach
    void setUp() {
        dealerApplicationService = new DealerApplicationService(
                dealerApplicationRepository, userRepository, dealerLicenseStorage);
    }

    @Test
    @DisplayName("접수된 신청은 관리자의 판정을 기다린다")
    void applyStartsPending() {
        givenValidLicense();
        givenApplicant();
        when(dealerApplicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DealerApplicationInfo info = dealerApplicationService.apply(APPLICANT_ID, LICENSE_KEY);

        assertThat(info.status()).isEqualTo(DealerApplicationStatus.PENDING);
        ArgumentCaptor<DealerApplication> captor = ArgumentCaptor.forClass(DealerApplication.class);
        verify(dealerApplicationRepository).save(captor.capture());
        assertThat(captor.getValue().getLicenseKey()).isEqualTo(LICENSE_KEY);
    }

    @Test
    @DisplayName("사원증 키가 없으면 거부한다")
    void applyRejectsMissingLicense() {
        assertThatThrownBy(() -> dealerApplicationService.apply(APPLICANT_ID, "  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.LICENSE_REQUIRED));

        verify(dealerLicenseStorage, never()).isValidUploadedDealerLicense(any());
        verify(dealerApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("업로드가 확인되지 않은 사원증 키면 거부한다")
    void applyRejectsUnverifiedLicense() {
        when(dealerLicenseStorage.isValidUploadedDealerLicense(LICENSE_KEY)).thenReturn(false);

        assertThatThrownBy(() -> dealerApplicationService.apply(APPLICANT_ID, LICENSE_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.INVALID_LICENSE));

        verify(dealerApplicationRepository, never()).save(any());
    }

    // 재신청 화면이 열려도 이 규칙은 그대로다 — 대기 중에는 막고 반려된 뒤에는 열린다
    @Test
    @DisplayName("심사 중인 신청이 있으면 새로 접수하지 않는다")
    void applyRejectsWhileAnotherIsPending() {
        givenValidLicense();
        when(dealerApplicationRepository
                .existsByApplicantIdAndStatus(APPLICANT_ID, DealerApplicationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> dealerApplicationService.apply(APPLICANT_ID, LICENSE_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.ALREADY_IN_PROGRESS));

        verify(dealerApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 접수로 사원증 제약이 위반되면 중복 사원증 예외로 변환한다")
    void applyConvertsDataIntegrityViolation() {
        givenValidLicense();
        givenApplicant();
        when(dealerApplicationRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'uk_dealer_application_license_key'"));

        assertThatThrownBy(() -> dealerApplicationService.apply(APPLICANT_ID, LICENSE_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DealerApplicationErrorCode.DUPLICATE_LICENSE));
    }

    private void givenValidLicense() {
        when(dealerLicenseStorage.isValidUploadedDealerLicense(LICENSE_KEY)).thenReturn(true);
    }

    private void givenApplicant() {
        when(userRepository.findById(APPLICANT_ID)).thenReturn(Optional.of(applicant()));
    }

    private static User applicant() {
        return User.create("race_kim", "race@race.kr", "$2a$10$encoded",
                "김레이스", "01012345678", Role.GENERAL);
    }
}
