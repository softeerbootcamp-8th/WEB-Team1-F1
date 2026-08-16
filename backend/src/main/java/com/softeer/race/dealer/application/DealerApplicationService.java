package com.softeer.race.dealer.application;

import static com.softeer.race.dealer.exception.DealerApplicationErrorCode.ALREADY_IN_PROGRESS;
import static com.softeer.race.dealer.exception.DealerApplicationErrorCode.APPLICANT_NOT_FOUND;
import static com.softeer.race.dealer.exception.DealerApplicationErrorCode.DUPLICATE_LICENSE;
import static com.softeer.race.dealer.exception.DealerApplicationErrorCode.INVALID_LICENSE;
import static com.softeer.race.dealer.exception.DealerApplicationErrorCode.LICENSE_REQUIRED;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationRepository;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 딜러 심사 신청 유스케이스.
 * <p>
 * 지금 이 서비스를 부르는 곳은 회원가입 하나뿐인데도 {@code UserService} 안에 두지 않는다.
 * 신청 화면을 따로 여는 순간(반려 후 재신청이 대표적이다) 컨트롤러만 얹으면 되도록,
 * <b>신청을 만드는 코드가 처음부터 한 곳에만 있게</b> 하려는 것이다. 가입이 신청을 직접 조립하면
 * 그때 검증과 생성이 두 벌로 갈라진다.
 */
@Service
@RequiredArgsConstructor
public class DealerApplicationService {

    private final DealerApplicationRepository dealerApplicationRepository;
    private final UserRepository userRepository;
    private final DealerLicenseStorage dealerLicenseStorage;

    /**
     * 사원증을 붙여 심사를 요청한다.
     * <p>
     * 검증을 호출자에게 맡기지 않고 여기서 한다. 나눠 두면 검증을 건너뛴 채 부를 수 있는 경로가
     * 생기고, 그 경로로 들어온 신청은 관리자가 열어 봐야 서류가 없다는 것을 안다.
     * <p>
     * 그 대가로 <b>가입 경로에서는 S3 확인이 DB 커넥션을 잡은 채 일어난다.</b> 가입 흐름은 회원을
     * 저장한 뒤에야 이 메서드를 부를 수 있어서다({@code applicantId}가 그때 생긴다).
     * 원래 {@code UserService}는 이 확인을 중복 조회보다 앞에 두어 커넥션을 잡기 전에 끝냈는데,
     * 그 순서를 지키려면 검증과 생성을 다시 갈라야 한다. 가입은 초당 몇 건이 오가는 경로가 아니라
     * 커넥션 하나를 HeadObject 한 번만큼 더 쥐는 쪽을 택했다.
     */
    @Transactional
    public DealerApplicationInfo apply(Long applicantId, String licenseKey) {
        validateLicense(licenseKey);
        if (dealerApplicationRepository
                .existsByApplicantIdAndStatus(applicantId, DealerApplicationStatus.PENDING)) {
            throw new BusinessException(ALREADY_IN_PROGRESS);
        }

        // getReferenceById 가 아니라 findById 다. 없는 계정이면 FK 위반이
        // DataIntegrityViolationException 이 되어 위의 사원증 중복으로 잘못 번역된다
        User applicant = userRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(APPLICANT_NOT_FOUND));

        return DealerApplicationInfo.from(save(DealerApplication.apply(applicant, licenseKey)));
    }

    private void validateLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank()) {
            throw new BusinessException(LICENSE_REQUIRED);
        }
        if (!dealerLicenseStorage.isValidUploadedDealerLicense(licenseKey)) {
            throw new BusinessException(INVALID_LICENSE);
        }
    }

    // 사전 검사와 저장 사이의 경합은 DB 제약이 잡아낸다. 이 테이블의 유니크 제약은 사원증 하나뿐이라
    // 제약명을 가려낼 필요 없이 하나의 코드로 옮긴다
    private DealerApplication save(DealerApplication application) {
        try {
            return dealerApplicationRepository.save(application);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(DUPLICATE_LICENSE);
        }
    }
}
