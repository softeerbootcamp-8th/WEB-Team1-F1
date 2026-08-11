package com.softeer.race.deal.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.deal.presentation.request.DeliveryConfirmRequest;
import com.softeer.race.deal.presentation.request.TransportSubmitRequest;
import com.softeer.race.deal.presentation.response.DealDetailResponse;
import com.softeer.race.deal.presentation.response.DealSliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Deal", description = "낙찰 이후 거래 API")
public interface DealApi {

    @Operation(summary = "내 거래 목록 조회",
            description = "내가 판매자이거나 구매자인 거래를 최근 것부터 10건씩 내려준다. 판매와 구매가 "
                    + "한 목록에 섞이며, mySide 로 어느 쪽인지 구분한다. 첫 요청은 cursor 없이 보내고 "
                    + "이후에는 직전 응답의 nextCursor 를 그대로 보낸다. 세션 쿠키가 필요하다.")
    ResponseEntity<DealSliceResponse> list(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "직전 응답의 nextCursor, 첫 요청에는 보내지 않는다", example = "7")
            Long cursor);

    @Operation(summary = "거래 상세 조회",
            description = "내가 당사자인 거래 하나를 내려준다. 없는 거래와 남의 거래는 모두 404 로 "
                    + "답하며 둘을 구분해 주지 않는다. 취소된 거래는 사유와 귀책이 함께 온다. "
                    + "세션 쿠키가 필요하다.")
    ResponseEntity<DealDetailResponse> detail(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "조회할 거래 식별자", example = "12")
            Long dealId);

    @Operation(summary = "구매 확정",
            description = "구매자가 거래를 진행하겠다고 확정한다. 판매자에게 서류와 탁송 일정을 "
                    + "요청하는 알림이 나간다. 지금 상대방 차례이면 403, 이미 지난 단계이면 409 다.")
    ResponseEntity<Void> confirmPurchase(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "거래 식별자", example = "12")
            Long dealId);

    @Operation(summary = "서류·탁송 일정 제출",
            description = "판매자가 서류 PDF 주소와 탁송 일시·장소를 낸다. 파일은 업로드 API 로 "
                    + "미리 올리고 조회 주소만 보낸다. 탁송 일시가 과거이면 400 이다.")
    ResponseEntity<Void> submitTransport(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "거래 식별자", example = "12")
            Long dealId,

            TransportSubmitRequest request);

    @Operation(summary = "인도 일정 확정",
            description = "구매자가 탁송 일정에 동의하고 인도 일시·장소를 잡는다. 이 호출로 거래가 "
                    + "확정되며 양쪽에 알림이 나간다. 인도 일시가 탁송 일시보다 앞서면 400 이다.")
    ResponseEntity<Void> confirmDelivery(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "거래 식별자", example = "12")
            Long dealId,

            DeliveryConfirmRequest request);

    @Operation(summary = "거래 취소",
            description = "당사자 누구든 확정 전까지 그만둘 수 있다. 그만둔 쪽이 귀책으로 남고 "
                    + "상대에게 알림이 나간다. 확정된 거래는 409 다.")
    ResponseEntity<Void> cancel(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "거래 식별자", example = "12")
            Long dealId);
}
