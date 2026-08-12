package com.softeer.race.auction.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auction.presentation.response.AuctionStartAlertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "AuctionStartAlert", description = "경매 시작 알림 신청 API")
public interface AuctionStartAlertApi {

    @Operation(summary = "경매 시작 알림 신청",
            description = "시작 전인 예정 경매에 시작 알림을 신청합니다. 경매가 실제로 시작되면 신청한 회원에게 "
                    + "알림이 한 번 발송되고, 알림을 누르면 해당 경매방으로 이동합니다. "
                    + "예정 시각이 지났더라도 실제로 시작되지 못한 경매에는 발송하지 않습니다. "
                    + "취소는 제공하지 않습니다. 신청 상태는 하나뿐이라 같은 요청을 반복해도 결과가 같고, "
                    + "처음 신청이면 201, 이미 신청돼 있었으면 204 입니다. 둘 다 성공이며 본문은 없습니다. "
                    + "발송이 끝나면 신청 기록이 정리되므로 시작 후에는 신청 여부를 조회할 수 없습니다.")
    @ApiResponse(responseCode = "201", description = "이번 요청으로 신청됐습니다.")
    @ApiResponse(responseCode = "204", description = "이미 신청돼 있어 상태가 그대로입니다.")
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매입니다.")
    @ApiResponse(responseCode = "409",
            description = "시작 전인 경매가 아닙니다(START_ALERT_NOT_OPEN). 이미 시작됐거나 끝난 경매입니다.")
    ResponseEntity<Void> subscribe(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            // 아규먼트 리졸버가 쿠키에서 채우는 값이라 요청에 실리지 않는다, 감추지 않으면 springdoc 이
            // 이름 그대로 필수 쿼리 파라미터로 문서에 내보낸다
            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);
    @Operation(summary = "경매 시작 알림 신청 여부 조회",
            description = "부르는 회원이 이 경매의 시작 알림을 신청했는지 반환합니다. 시작 전 미리보기와 "
                    + "경매 대기방이 같은 상태를 보기 위해 쓰는 조회입니다. "
                    + "발송이 끝나면 신청 기록이 정리되므로, 경매가 시작된 뒤에는 신청했던 회원에게도 false 가 "
                    + "돌아갑니다. 시작 후에는 신청 자체가 불가능해 화면에 신청 버튼이 없습니다. "
                    + "없는 경매에도 404 가 아니라 false 로 답합니다. 이 조회는 경매의 존재가 아니라 회원의 "
                    + "신청 여부를 답하며, 경매 존재 여부는 화면을 띄운 본 요청이 이미 판정합니다.")
    @ApiResponse(responseCode = "200", description = "신청 여부입니다.")
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    ResponseEntity<AuctionStartAlertResponse> readSubscription(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);
}