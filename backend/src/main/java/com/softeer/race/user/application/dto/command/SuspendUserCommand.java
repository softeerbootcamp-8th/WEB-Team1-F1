package com.softeer.race.user.application.dto.command;

/**
 * 이용정지 유스케이스의 입력.
 *
 * @param reason 관리자가 남기는 정지 사유. 당사자에게는 내려가지 않고 관리자 화면에서만 보인다
 */
public record SuspendUserCommand(
        Long userId,
        String reason
) {
}
