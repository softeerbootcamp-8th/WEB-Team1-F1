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

/** 세션의 발급 · 검증 · 폐기. 세션 저장 형태(해시)를 아는 유일한 지점이다. */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionStore sessionStore;
    private final SessionTokenGenerator sessionTokenGenerator;
    private final AuthProperties authProperties;

    /** 쿠키에 담을 원문 토큰을 반환한다. 저장소에는 해시만 남아 원문을 되돌릴 수 없다. */
    public String issue(User user) {
        String rawToken = sessionTokenGenerator.generate();
        sessionStore.save(
                sessionTokenGenerator.hash(rawToken),
                new AuthenticatedUser(user.getId(), user.getRole()),
                authProperties.session().ttl());
        return rawToken;
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
    public AuthenticatedUser authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(UNAUTHENTICATED);
        }

        String hashedToken = sessionTokenGenerator.hash(rawToken);
        AuthenticatedUser authenticatedUser = sessionStore.find(hashedToken)
                .orElseThrow(() -> new BusinessException(UNAUTHENTICATED));

        Duration renewThreshold = authProperties.session().renewThreshold();
        if (sessionStore.timeToLive(hashedToken).compareTo(renewThreshold) <= 0) {
            sessionStore.extend(hashedToken, authProperties.session().ttl());
        }

        return authenticatedUser;
    }

    /** 없는 토큰이어도 예외를 던지지 않는다. 로그아웃은 멱등해야 한다. */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionStore.delete(sessionTokenGenerator.hash(rawToken));
    }
}
