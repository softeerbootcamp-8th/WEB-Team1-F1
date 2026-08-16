package com.softeer.race.dealer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerApplicationRepository extends JpaRepository<DealerApplication, Long> {

    /**
     * 심사 중인 신청이 이미 있는지. 재신청이 열려도 이 판정은 그대로다 —
     * 대기 중에는 막고 결론이 난 뒤에는 새 신청을 허용한다.
     */
    boolean existsByApplicantIdAndStatus(Long applicantId, DealerApplicationStatus status);
}
