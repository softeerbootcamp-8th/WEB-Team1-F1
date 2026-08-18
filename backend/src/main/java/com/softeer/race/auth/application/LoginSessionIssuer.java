package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.ACCOUNT_SUSPENDED;
import static com.softeer.race.auth.exception.AuthErrorCode.INVALID_CREDENTIALS;

import com.softeer.race.auth.application.dto.info.AuthUserInfo;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 검증이 끝난 로그인의 상태 재확인과 세션 발급을 한 임계 구역으로 묶는다. */
@Service
@RequiredArgsConstructor
public class LoginSessionIssuer {

    private final UserRepository userRepository;
    private final SessionService sessionService;

    /**
     * 사용자 행을 잠근 채 최신 이용 상태를 확인하고 세션을 발급한다.
     * <p>
     * 정지도 같은 행을 잠그므로 둘의 순서는 둘 중 하나로만 정해진다. 로그인이 먼저면 뒤따른 정지가
     * 방금 발급한 세션까지 지우고, 정지가 먼저면 여기서 정지 상태를 읽어 세션을 만들지 않는다.
     * bcrypt는 이 트랜잭션 앞에서 끝나므로 느린 비밀번호 검증 동안 DB 커넥션과 행 잠금을 점유하지 않는다.
     */
    @Transactional
    public LoginInfo issue(long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                // 자격 검증 뒤 회원이 사라진 드문 경합도 계정 존재 여부를 더 드러내지 않는다
                .orElseThrow(() -> new BusinessException(INVALID_CREDENTIALS));

        if (user.isSuspended()) {
            throw new BusinessException(ACCOUNT_SUSPENDED);
        }

        return new LoginInfo(sessionService.issue(user), AuthUserInfo.from(user));
    }
}
