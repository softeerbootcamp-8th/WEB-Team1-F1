package com.softeer.race.evaluation.presentation.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.softeer.race.vehicle.domain.VehicleKeyword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상한 상수가 키워드 종류 수와 어긋나지 않는지.
 * <p>
 * 애노테이션 인자는 컴파일 타임 상수여야 해서 {@code VehicleKeyword.values().length}를 쓸 수 없고,
 * 그래서 상수를 손으로 적었다. 키워드를 추가하면서 이 상수를 잊으면 <b>키워드를 다 매긴 정상 요청이
 * 400으로 거부된다</b> — 그 어긋남을 여기서 잡는다.
 */
@DisplayName("평가 결과 제출 요청")
class EvaluationResultSubmitRequestTest {

    @Test
    @DisplayName("키워드 개수 상한은 키워드 종류 수와 같다")
    void keywordLimitMatchesKeywordCount() {
        assertThat(EvaluationResultSubmitRequest.MAX_KEYWORD_COUNT)
                .isEqualTo(VehicleKeyword.values().length);
    }
}
