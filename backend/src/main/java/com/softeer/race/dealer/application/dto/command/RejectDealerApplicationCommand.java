package com.softeer.race.dealer.application.dto.command;

/**
 * 딜러 심사 반려 유스케이스의 입력.
 *
 * @param reason 신청자에게 전달할 반려 사유
 */
public record RejectDealerApplicationCommand(
        Long applicationId,
        String reason
) {
}
