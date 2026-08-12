package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.request.VisitQuoteRequest;
import com.softeer.race.evaluation.presentation.request.VisitQuotePrecheckRequest;
import com.softeer.race.evaluation.presentation.response.VisitQuotePrecheckResponse;
import com.softeer.race.evaluation.presentation.response.VisitQuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "VisitQuote", description = "방문견적 신청 API")
public interface VisitQuoteApi {

    @Operation(summary = "방문견적 신청 사전 확인",
            description = "번호판과 소유자명으로 차량을 확인하고 같은 차량의 진행 중인 방문견적이 있는지 "
                    + "돌려줍니다. 예약 화면 진입 전에 호출하며 세션 로그인이 필요합니다. REQUESTED와 "
                    + "APPROVED는 진행 중으로 보고, REJECTED만 있는 차량은 다시 신청할 수 있습니다. "
                    + "최종 신청 API도 중복 여부를 다시 검사합니다.")
    @ApiResponse(responseCode = "403", description = "평가사 역할은 방문견적을 사전 확인할 수 없습니다.")
    ResponseEntity<VisitQuotePrecheckResponse> precheck(
            AuthenticatedUser authenticatedUser, VisitQuotePrecheckRequest request);

    @Operation(summary = "방문견적 신청",
            description = "예상 시세를 확인한 판매자가 방문 희망 장소 · 날짜 · 연락처를 보내 평가사 방문을 신청합니다. "
                    + "서버가 번호판과 소유자명으로 제원을 재조회해 차량을 등록하고, 평가사 배정을 기다리는 "
                    + "REQUESTED 상태의 신청을 만듭니다. 제원은 클라이언트가 보낸 값을 쓰지 않습니다. "
                    + "소유자명은 시세 조회에 입력한 값과 같아야 하며, 어긋나면 미등록 번호판과 같은 404가 됩니다. "
                    + "주행거리와 예상 시세는 받지도, 산정하지도 않습니다 — 실측과 시세 산정은 평가사가 "
                    + "방문해서 하는 일이라 차량은 그 두 값이 빈 상태로 등록됩니다. "
                    + "같은 번호판으로 진행 중인 신청이 이미 있으면 409로 거부합니다. "
                    + "경매글과 경매는 만들어지지 않습니다 — 출품은 진단이 끝난 뒤의 단계입니다. "
                    + "세션 쿠키가 필요합니다.")
    @ApiResponse(responseCode = "403", description = "평가사 역할은 방문견적을 신청할 수 없습니다.")
    ResponseEntity<VisitQuoteResponse> request(
            AuthenticatedUser authenticatedUser, VisitQuoteRequest request);
}
