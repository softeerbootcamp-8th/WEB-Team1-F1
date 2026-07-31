package com.softeer.race.quote.presentation;

import com.softeer.race.quote.presentation.request.QuoteRequest;
import com.softeer.race.quote.presentation.response.QuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Quote", description = "차량 예상 시세 조회 API")
public interface QuoteApi {

    @Operation(summary = "예상 시세 조회",
            description = "번호판과 소유자명으로 차량 제원을 찾아 예상 시세를 함께 내려줍니다. "
                    + "로그인이 필요 없습니다. 시세는 서버가 기준가에서 연식·주행거리 감가를 빼서 산정하며, "
                    + "기준가 자체는 응답에 넣지 않습니다. "
                    + "미등록 번호판과 소유자명 불일치는 같은 404로 응답합니다 — "
                    + "구분해서 알려주면 번호판을 바꿔 넣어보며 소유자명을 알아낼 수 있습니다. "
                    + "조회인데 POST 인 이유는 번호판과 실명을 쿼리 파라미터로 보내면 "
                    + "액세스 로그와 브라우저 히스토리에 그대로 남기 때문입니다.")
    ResponseEntity<QuoteResponse> estimate(QuoteRequest request);
}
