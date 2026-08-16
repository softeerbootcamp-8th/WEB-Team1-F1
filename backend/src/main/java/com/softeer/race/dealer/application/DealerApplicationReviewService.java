package com.softeer.race.dealer.application;

import static com.softeer.race.dealer.exception.DealerApplicationErrorCode.NOT_FOUND;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.dealer.application.dto.command.RejectDealerApplicationCommand;
import com.softeer.race.dealer.application.dto.info.DealerApplicationDetailInfo;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.application.dto.info.DealerApplicationSummaryInfo;
import com.softeer.race.dealer.domain.DealerApplication;
import com.softeer.race.dealer.domain.DealerApplicationRepository;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.storage.domain.DealerLicenseStorage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 딜러 심사 유스케이스.
 * <p>
 * 신청을 만드는 {@link DealerApplicationService}와 나눠 둔다. 부르는 사람도(신청자 · 관리자),
 * 지켜야 할 규칙도(중복 신청 차단 · 재판정 차단) 겹치지 않아서, 한 클래스에 두면 두 흐름의 검증이
 * 서로의 맥락을 모른 채 섞인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealerApplicationReviewService {

    private final DealerApplicationRepository dealerApplicationRepository;
    private final DealerLicenseStorage dealerLicenseStorage;

    /** 해당 상태의 신청 전량을 접수 순으로. 나누어 읽지 않는 이유는 리포지토리에 적어 두었다. */
    public List<DealerApplicationSummaryInfo> findAllByStatus(DealerApplicationStatus status) {
        return dealerApplicationRepository.findAllByStatus(status).stream()
                .map(DealerApplicationSummaryInfo::from)
                .toList();
    }

    /**
     * 신청 상세. 사원증을 볼 임시 주소를 이 시점에 발급한다.
     * <p>
     * 목록이 아니라 여기서 서명하는 이유는 두 가지다. 목록 스무 건을 서명해도 관리자가 여는 것은
     * 한 건이고, 서명된 주소는 발급하는 만큼 새어 나갈 자리가 늘기 때문이다.
     */
    public DealerApplicationDetailInfo findDetail(Long applicationId) {
        DealerApplication application = dealerApplicationRepository.findDetailById(applicationId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND));

        return DealerApplicationDetailInfo.from(application,
                dealerLicenseStorage.presignDealerLicenseView(application.getLicenseKey()));
    }

    /**
     * 승인한다. 신청자에게 딜러 자격이 붙는 것까지가 이 한 번의 트랜잭션이다.
     * <p>
     * <b>승인 결과가 그 회원의 세션에는 바로 반영되지 않는다.</b> 역할이 로그인 시점에 세션으로
     * 복사되므로, 이미 로그인해 있던 신청자는 다시 로그인하거나 세션이 만료될 때까지 일반 회원으로
     * 동작한다. 그 세션을 폐기하는 일은 별도 이슈로 다룬다.
     */
    @Transactional
    public DealerApplicationInfo approve(Long applicationId) {
        DealerApplication application = lockForDecision(applicationId);

        application.validateDecidable();
        // 잠금 조회가 join fetch 없이 읽으므로 여기서 신청자 프록시 초기화 쿼리가 한 번 나간다 —
        // 회원 행까지 잠그지 않으려고 치르는 비용이고, 트랜잭션 안이라 지연 로딩이 성립한다
        application.approve();

        return DealerApplicationInfo.from(application);
    }

    /** 사유를 남겨 반려한다. 신청자의 역할은 그대로 일반 회원이다. */
    @Transactional
    public DealerApplicationInfo reject(RejectDealerApplicationCommand command) {
        DealerApplication application = lockForDecision(command.applicationId());

        application.validateDecidable();
        application.reject(command.reason());

        return DealerApplicationInfo.from(application);
    }

    private DealerApplication lockForDecision(Long applicationId) {
        return dealerApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND));
    }
}
