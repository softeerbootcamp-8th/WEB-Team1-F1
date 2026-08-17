package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.UNAUTHENTICATED;

import com.softeer.race.auth.config.AuthProperties;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.auth.domain.SessionTokenGenerator;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.User;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 세션의 발급 · 검증 · 폐기 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionStore sessionStore;
    private final SessionTokenGenerator sessionTokenGenerator;
    private final AuthProperties authProperties;

    /** 쿠키에 담을 토큰을 반환한다. 그 토큰이 그대로 저장소의 키다. */
    public String issue(User user) {
        String token = sessionTokenGenerator.generate();
        sessionStore.save(
                token,
                new AuthenticatedUser(user.getId(), user.getRole()),
                authProperties.session().ttl());
        return token;
    }

    /**
     * 세션을 검증하고 필요하면 만료를 연장한다.
     * <p>
     * 만료 판정은 저장소의 TTL이 대신한다. 만료된 세션은 저장소에서 이미 사라져 있으므로 여기서
     * 만료와 부재를 구분하지 않고 <b>둘 다 미인증</b>으로 다룬다.
     * <p>
     * 남은 시간이 임계값 이하일 때만 수명을 다시 잡는다. 저장소 쓰기가 세션당 임계값에 한 번으로
     * 묶여 요청 빈도와 무관해지는 대신, 체감 유휴 타임아웃은 정확히 ttl이 아니라
     * {@code [ttl - renewThreshold, ttl]} 구간이 된다.
     */
    public AuthenticatedUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(UNAUTHENTICATED);
        }

        AuthenticatedUser authenticatedUser = sessionStore.find(token)
                .orElseThrow(() -> new BusinessException(UNAUTHENTICATED));

        Duration renewThreshold = authProperties.session().renewThreshold();
        if (sessionStore.timeToLive(token).compareTo(renewThreshold) <= 0) {
            sessionStore.extend(token, authenticatedUser.id(), authProperties.session().ttl());
        }

        return authenticatedUser;
    }

    /** 없는 토큰이어도 예외를 던지지 않는다. 로그아웃은 멱등해야 한다. */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionStore.delete(token);
    }

    /**
     * 이 회원의 모든 세션을 끊는다. <b>역할을 바꾼 쪽이 반드시 함께 불러야 한다.</b>
     * <p>
     * 세션에 복사된 역할은 로그인 시점의 스냅샷이라, 부르지 않으면 그 회원은 최대 TTL만큼 바뀌기 전
     * 권한으로 계속 요청할 수 있다. 승격이라면 새 권한이 늦게 붙는 데서 그치지만, 권한을 뺏는
     * 변경에서는 그 시간만큼 그대로 열려 있는 것이 된다.
     * <p>
     * 세션을 갱신하지 않고 끊는 이유는 두 방향을 한 연산으로 다루기 위해서다. 값을 새 역할로
     * 덮어쓰면 승격은 매끄럽지만 회수에는 쓸 수 없어, 결국 경로가 둘로 갈린다. 대신 당사자는
     * 다시 로그인해야 한다.
     */
    public void revokeAllOf(long userId) {
        sessionStore.deleteAllOf(userId);
    }
}
