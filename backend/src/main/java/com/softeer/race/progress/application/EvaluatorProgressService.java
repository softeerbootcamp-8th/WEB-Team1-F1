package com.softeer.race.progress.application;

import com.softeer.race.progress.application.dto.EvaluatorTaskInfo;
import com.softeer.race.progress.domain.EvaluatorTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 평가사가 자기 일감을 보는 조회
 * <p>
 * TODO 역할 기반 인가가 들어오면 평가사(EVALUATOR)로 좁힌다. 지금은 로그인만 확인하므로 평가사가
 * 아닌 회원도 아직 배정되지 않은 신청 목록을 열 수 있고, 거기에는 신청자 이름과 방문 희망 장소가
 * 실려 있다. {@code VehicleImageController} · {@code ImageUploadController}에 같은 TODO가 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluatorProgressService {

    private final EvaluatorTaskRepository evaluatorTaskRepository;

    public List<EvaluatorTaskInfo> listTasks(long userId) {
        return evaluatorTaskRepository.findAllForEvaluator(userId).stream()
                .map(EvaluatorTaskInfo::from)
                .toList();
    }
}
