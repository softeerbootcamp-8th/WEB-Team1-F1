package com.softeer.race.user.presentation;

import com.softeer.race.user.application.UserSuspensionService;
import com.softeer.race.user.presentation.request.UserSuspendRequest;
import com.softeer.race.user.presentation.response.UserStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 회원 관리 API.
 * <p>
 * <b>메서드에 {@code @RequireRole(ADMIN)}을 붙이지 않는다.</b> 경로가 {@code /api/admin/**}이라
 * AuthInterceptor가 애너테이션과 무관하게 인증과 ADMIN을 요구하기 때문이다. 붙여 두면 그 애너테이션이
 * 지키고 있는 것처럼 읽혀서, 다음 사람이 새 핸들러에 빠뜨렸을 때 진짜 방어선이 무엇인지 가려진다
 * ({@code AdminDealerApplicationController}와 같은 이유다).
 * <p>
 * <b>누가 눌렀는지를 {@code @LoginUser}로 받지 않는다.</b> 이 경로를 통과한 사람은 언제나 관리자이고,
 * 정지 대상은 일반 회원과 딜러로 한정돼 있어({@code Role.isSuspendable}) 요청자와 대상이 같아질 수
 * 없다. 자기 자신인지 비교하는 코드를 두면 절대 참이 되지 않는 검사가 방어선처럼 읽힌다.
 */
@Tag(name = "AdminUser", description = "관리자 회원 관리 API")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserSuspensionService userSuspensionService;

    /**
     * 200이다. 새로 조회할 자원이 생기지 않고, 기존 회원의 이용 상태가 옮겨질 뿐이다
     * ({@code AdminDealerApplicationController.approve}와 같다).
     */
    @Operation(summary = "회원 이용정지",
            description = "일반 회원 또는 딜러의 서비스 이용을 정지하고, 그 회원의 세션을 모두 끊습니다.")
    @PostMapping("/{userId}/suspension")
    public UserStatusResponse suspend(
            @PathVariable Long userId,
            @Valid @RequestBody UserSuspendRequest request) {

        return UserStatusResponse.from(userSuspensionService.suspend(request.toCommand(userId)));
    }

    /**
     * 정지와 한 엔드포인트로 합치지 않는다. 두 조작은 입력이 겹치지 않아서다 — 정지만 사유를 받고
     * 해제는 아무것도 받지 않는다. 하나로 묶으면 어느 쪽에도 필수가 아닌 값만 남아 검증할 것이 없어진다.
     */
    @Operation(summary = "회원 이용정지 해제", description = "정지된 회원의 서비스 이용을 다시 엽니다.")
    @PostMapping("/{userId}/activation")
    public UserStatusResponse activate(@PathVariable Long userId) {
        return UserStatusResponse.from(userSuspensionService.activate(userId));
    }
}
