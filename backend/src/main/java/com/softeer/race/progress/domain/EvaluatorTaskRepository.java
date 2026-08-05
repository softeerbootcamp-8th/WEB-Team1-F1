package com.softeer.race.progress.domain;

import com.softeer.race.evaluation.domain.Evaluation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluatorTaskRepository extends Repository<Evaluation, Long> {

    /**
     * 내가 맡은 신청과 아직 아무도 맡지 않은 신청
     * <p>
     * 미배정을 함께 내리는 이유는 배정 기능이 아직 없어서다. 그것까지 보이지 않으면 평가사 화면이
     * 언제나 비어 있고, 들어올 일감이 있는지도 알 수 없다. 배정이 붙으면 이 조건에서 미배정을
     * 떼어내고 배정 대기 목록을 따로 두게 된다.
     * <p>
     * 다른 평가사가 맡은 건은 보이지 않는다. 남의 담당까지 보여줄 이유가 없고, 신청자 주소가
     * 함께 실려 나간다.
     */
    @Query("""
            select new com.softeer.race.progress.domain.EvaluatorTaskRow(
                e.id, e.status, ev.id, e.visitDate, e.visitAddress, e.createdAt,
                v.id, v.manufacturer, v.model, v.modelYear, v.plateNumber, s.realName)
            from Evaluation e
            join e.vehicle v
            join v.seller s
            left join e.evaluator ev
            where ev is null or ev.id = :evaluatorId
            order by e.visitDate, e.id
            """)
    List<EvaluatorTaskRow> findAllForEvaluator(@Param("evaluatorId") long evaluatorId);
}
