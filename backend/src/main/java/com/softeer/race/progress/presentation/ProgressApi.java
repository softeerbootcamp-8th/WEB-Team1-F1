package com.softeer.race.progress.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.progress.presentation.response.EvaluatorTaskListResponse;
import com.softeer.race.progress.presentation.response.SellerProgressDetailResponse;
import com.softeer.race.progress.presentation.response.SellerProgressListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Progress", description = "판매자 · 평가사 진행 상황 조회 API")
public interface ProgressApi {

    @Operation(summary = "내 진행 상황 목록 조회",
            description = "로그인한 판매자가 낸 신청을 최근 순으로 내려준다. 판매 신청과 방문견적 신청이 한 목록에 "
                    + "섞이며, 어느 쪽으로 들어왔는지는 stage로 구분된다. 방문견적으로 들어온 건은 평가 단계에서 "
                    + "시작하고 판매 신청으로 들어온 건은 경매 단계에서 시작한다.")
    ResponseEntity<SellerProgressListResponse> listMine(AuthenticatedUser user);

    @Operation(summary = "내 진행 상황 상세 조회",
            description = "방문 예정일 · 반려 사유 · 경매 금액처럼 특정 단계에서만 의미가 있는 값까지 함께 내려준다. "
                    + "내 차량이 아니면 404다.")
    ResponseEntity<SellerProgressDetailResponse> detail(AuthenticatedUser user, long vehicleId);

    @Operation(summary = "평가사 일감 목록 조회",
            description = "내가 맡은 신청과 아직 아무 평가사도 배정되지 않은 신청을 방문 희망일이 빠른 순으로 "
                    + "내려준다. 지금은 로그인만 확인하며, 역할 기반 인가가 들어오면 평가사로 좁힌다.")
    ResponseEntity<EvaluatorTaskListResponse> listEvaluatorTasks(AuthenticatedUser user);
}
