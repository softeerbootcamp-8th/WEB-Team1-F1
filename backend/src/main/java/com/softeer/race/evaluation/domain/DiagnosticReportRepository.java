package com.softeer.race.evaluation.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, Long> {

    /**
     * 평가에 이미 붙은 진단서. 첨부가 최초인지 교체인지 가르는 데 쓴다.
     * <p>
     * {@code evaluation_id}에 unique 제약이 있어 한 건을 넘게 돌려줄 수 없다.
     */
    Optional<DiagnosticReport> findByEvaluationId(Long evaluationId);
}
