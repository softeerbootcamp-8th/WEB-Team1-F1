package com.softeer.race.dealer.presentation;

import com.softeer.race.dealer.application.DealerApplicationReviewService;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.dealer.presentation.request.DealerApplicationRejectRequest;
import com.softeer.race.dealer.presentation.response.DealerApplicationDecisionResponse;
import com.softeer.race.dealer.presentation.response.DealerApplicationDetailResponse;
import com.softeer.race.dealer.presentation.response.DealerApplicationsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 딜러 심사 API.
 * <p>
 * <b>메서드에 {@code @RequireRole(ADMIN)}을 붙이지 않는다.</b> 경로가 {@code /api/admin/**}이라
 * AuthInterceptor가 애너테이션과 무관하게 인증과 ADMIN을 요구하기 때문이다. 붙여 두면 그 애너테이션이
 * 지키고 있는 것처럼 읽혀서, 다음 사람이 새 핸들러에 빠뜨렸을 때 <b>여기서 진짜 방어선이 무엇인지</b>
 * 가려진다. 지키는 것은 경로 하나이고, 그 사실이 코드에서도 하나로 보여야 한다.
 */
@Tag(name = "AdminDealerApplication", description = "관리자 딜러 심사 API")
@RestController
@RequestMapping("/api/admin/dealer-applications")
@RequiredArgsConstructor
public class AdminDealerApplicationController {

    private final DealerApplicationReviewService dealerApplicationReviewService;

    @Operation(summary = "딜러 심사 신청 목록",
            description = "상태로 좁혀 접수 순으로 전량을 돌려줍니다. 기본값은 심사 대기(PENDING)입니다.")
    @GetMapping
    public DealerApplicationsResponse findAll(
            @RequestParam(defaultValue = "PENDING") DealerApplicationStatus status) {

        return DealerApplicationsResponse.from(
                dealerApplicationReviewService.findAllByStatus(status));
    }

    @Operation(summary = "딜러 심사 신청 상세",
            description = "신청자 정보와 사원증을 볼 수 있는 임시 주소를 함께 돌려줍니다.")
    @GetMapping("/{applicationId}")
    public DealerApplicationDetailResponse findDetail(@PathVariable Long applicationId) {
        return DealerApplicationDetailResponse.from(
                dealerApplicationReviewService.findDetail(applicationId));
    }

    /**
     * 200이다. 새로 조회할 자원이 생기지 않고, 기존 신청의 상태가 끝으로 옮겨질 뿐이다
     * ({@code EvaluationResultController.reject}와 같다).
     */
    @Operation(summary = "딜러 심사 승인",
            description = "신청을 승인하고 신청자에게 딜러 자격을 부여합니다.")
    @PostMapping("/{applicationId}/approval")
    public DealerApplicationDecisionResponse approve(@PathVariable Long applicationId) {
        return DealerApplicationDecisionResponse.from(
                dealerApplicationReviewService.approve(applicationId));
    }

    /**
     * 승인과 한 엔드포인트로 합치지 않는다. 두 판정은 입력이 겹치지 않아서다 — 반려만 사유를 받고
     * 승인은 아무것도 받지 않는다. 하나로 묶으면 어느 쪽에도 필수가 아닌 값만 남아 검증할 것이 없어진다.
     */
    @Operation(summary = "딜러 심사 반려", description = "사유를 남겨 신청을 반려합니다.")
    @PostMapping("/{applicationId}/rejection")
    public DealerApplicationDecisionResponse reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody DealerApplicationRejectRequest request) {

        return DealerApplicationDecisionResponse.from(
                dealerApplicationReviewService.reject(request.toCommand(applicationId)));
    }
}
