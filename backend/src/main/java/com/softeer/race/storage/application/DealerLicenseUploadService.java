package com.softeer.race.storage.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.application.dto.info.DealerLicenseUploadInfo;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import com.softeer.race.storage.domain.UploadContentType;
import com.softeer.race.storage.exception.StorageErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 비회원이 회원가입 전에 자동차매매사원증 한 건을 직접 업로드할 주소를 발급한다. */
@Service
@RequiredArgsConstructor
public class DealerLicenseUploadService {

    private final DealerLicenseStorage dealerLicenseStorage;

    /**
     * {@code contentType}이 {@code UploadContentType}이 아니라 문자열이다. enum으로 받으면 변환이
     * 컨트롤러에서 끝나 "어떤 형식을 사원증으로 받기로 했는가"라는 판정을 서비스가 건너뛴다.
     * 그러면 서비스 테스트가 잘못된 형식을 넣어 볼 수 없고(enum 자리에 {@code "image/webp"}를
     * 넘기는 코드는 컴파일되지 않는다) 다른 호출자가 생기면 그 판정도 함께 빠진다.
     */
    public DealerLicenseUploadInfo issue(String contentType, long contentLength) {
        UploadContentType uploadContentType = dealerLicenseTypeOf(contentType);

        if (contentLength > UploadContentType.MAX_DEALER_LICENSE_SIZE) {
            throw new BusinessException(StorageErrorCode.DEALER_LICENSE_TOO_LARGE);
        }

        return DealerLicenseUploadInfo.from(
                dealerLicenseStorage.presignDealerLicense(uploadContentType, contentLength));
    }

    /**
     * 아예 모르는 형식이든 우리가 아는 형식이지만 사원증으로 받지 않는 형식(WEBP)이든 같은 코드로
     * 답한다. 사원증 발급에서는 둘 다 "이 형식으로는 사원증을 낼 수 없다"는 한 가지 사실이고,
     * 여기서 {@code STORAGE_UNSUPPORTED_TYPE}이 새어 나가면 프론트가 두 가지 안내를 갈라 두게 된다.
     */
    private UploadContentType dealerLicenseTypeOf(String contentType) {
        UploadContentType uploadContentType;
        try {
            uploadContentType = UploadContentType.from(contentType);
        } catch (BusinessException exception) {
            throw new BusinessException(StorageErrorCode.UNSUPPORTED_DEALER_LICENSE_TYPE);
        }

        if (!uploadContentType.isDealerLicenseAllowed()) {
            throw new BusinessException(StorageErrorCode.UNSUPPORTED_DEALER_LICENSE_TYPE);
        }

        return uploadContentType;
    }
}
