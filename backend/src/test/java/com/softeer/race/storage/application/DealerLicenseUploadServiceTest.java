package com.softeer.race.storage.application;

import static com.softeer.race.storage.exception.StorageErrorCode.DEALER_LICENSE_TOO_LARGE;
import static com.softeer.race.storage.exception.StorageErrorCode.UNSUPPORTED_DEALER_LICENSE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.application.dto.info.DealerLicenseUploadInfo;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import com.softeer.race.storage.domain.PresignedDealerLicense;
import com.softeer.race.storage.domain.UploadContentType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("자동차매매사원증 업로드 서비스")
class DealerLicenseUploadServiceTest {

    @Mock
    private DealerLicenseStorage dealerLicenseStorage;

    private DealerLicenseUploadService service;

    @BeforeEach
    void setUp() {
        service = new DealerLicenseUploadService(dealerLicenseStorage);
    }

    @Test
    @DisplayName("JPEG 한 건의 비공개 업로드 주소를 발급한다")
    void issue() {
        PresignedDealerLicense upload = new PresignedDealerLicense(
                "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg",
                "https://s3/upload", LocalDateTime.of(2026, 8, 11, 12, 0));
        when(dealerLicenseStorage.presignDealerLicense(UploadContentType.JPEG, 1024L))
                .thenReturn(upload);

        DealerLicenseUploadInfo result = service.issue("image/jpeg", 1024L);

        assertThat(result.key()).isEqualTo(upload.key());
        assertThat(result.uploadUrl()).isEqualTo(upload.uploadUrl());
        verify(dealerLicenseStorage).presignDealerLicense(UploadContentType.JPEG, 1024L);
    }

    @Test
    @DisplayName("WEBP는 사원증 형식으로 허용하지 않는다")
    void rejectWebp() {
        assertThatThrownBy(() -> service.issue("image/webp", 1024L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UNSUPPORTED_DEALER_LICENSE_TYPE));
    }

    @Test
    @DisplayName("PDF도 10MB를 넘으면 거부한다")
    void rejectLargePdf() {
        assertThatThrownBy(() -> service.issue(
                "application/pdf", UploadContentType.MAX_DEALER_LICENSE_SIZE + 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DEALER_LICENSE_TOO_LARGE));
    }
}
