package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.INVALID_CREDENTIALS;
import static com.softeer.race.auth.exception.AuthErrorCode.UNAUTHENTICATED;

import com.softeer.race.auth.application.dto.command.LoginCommand;
import com.softeer.race.auth.application.dto.info.AuthUserInfo;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    // 실측값, 73바이트부터 at.favre.lib bcrypt의 verify가 IllegalArgumentException을 던진다
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    /**
     * 다른 서비스와 달리 @Transactional을 붙이지 않는다. bcrypt 검증에 100ms 가까이 걸리는데 그 시간만큼
     * DB 커넥션을 점유할 이유가 없고, 조회와 세션 발급 사이에 원자성이 필요한 지점도 없다.
     * 세션 INSERT의 트랜잭션은 Repository의 save가 담당한다.
     */
    public LoginInfo login(LoginCommand command) {
        // 아이디가 없을 때와 비밀번호가 틀릴 때 같은 예외를 던져야 계정 존재 여부가 드러나지 않는다
        User user = userRepository.findByUsername(command.username())
                .orElseThrow(() -> new BusinessException(INVALID_CREDENTIALS));
        if (!matches(command.password(), user.getPassword())) {
            throw new BusinessException(INVALID_CREDENTIALS);
        }

        return new LoginInfo(sessionService.issue(user), AuthUserInfo.from(user));
    }

    public void logout(String token) {
        sessionService.revoke(token);
    }

    @Transactional(readOnly = true)
    public AuthUserInfo me(long userId) {
        return userRepository.findById(userId)
                .map(AuthUserInfo::from)
                // 세션은 살아 있는데 회원이 사라진 경우, 없는 리소스가 아니라 인증 실패로 다룬다
                .orElseThrow(() -> new BusinessException(UNAUTHENTICATED));
    }

    /**
     * 가입 요청은 ASCII @Pattern으로 1자 = 1바이트가 보장되지만 로그인 요청에는 그 보장이 없다.
     * 64자 한글은 192바이트라 그대로 넘기면 bcrypt가 예외를 던져 500이 된다.
     * 검증을 시도하지 않고 자격 오류로 응답하면 길이 정책도 노출되지 않는다.
     */
    private boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
