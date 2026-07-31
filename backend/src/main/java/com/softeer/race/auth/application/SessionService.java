package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.SESSION_EXPIRED;
import static com.softeer.race.auth.exception.AuthErrorCode.UNAUTHENTICATED;

import com.softeer.race.auth.config.AuthProperties;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionTokenGenerator;
import com.softeer.race.auth.domain.UserSession;
import com.softeer.race.auth.domain.UserSessionRepository;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.User;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 세션의 발급 · 검증 · 폐기. 세션 저장 형태(해시)를 아는 유일한 지점이다. */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final SessionTokenGenerator sessionTokenGenerator;
    private final AuthProperties authProperties;
    private final Clock clock;

    /** 쿠키에 담을 원문 토큰을 반환한다. DB에는 해시만 남아 원문을 되돌릴 수 없다. */
    public String issue(User user) {
        String rawToken = sessionTokenGenerator.generate();
        UserSession session = UserSession.issue(
                sessionTokenGenerator.hash(rawToken), user, now(), authProperties.session().ttl());
        userSessionRepository.save(session);
        return rawToken;
    }

    /**
     * 세션을 검증하고 필요하면 만료를 연장한다.
     * <p>
     * 인터셉터의 preHandle은 트랜잭션 밖에서 실행되므로 반드시 이 메서드를 경유해야 지연 로딩과
     * 더티 체킹이 동작한다. 이 트랜잭션은 핸들러 실행 전에 커밋되어, 컨트롤러가 롤백돼도
     * 연장은 유지된다. 세션 수명은 업무 트랜잭션의 성패와 무관하므로 의도된 동작이다.
     */
    @Transactional
    public AuthenticatedUser authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(UNAUTHENTICATED);
        }

        UserSession session = userSessionRepository.findById(sessionTokenGenerator.hash(rawToken))
                .orElseThrow(() -> new BusinessException(UNAUTHENTICATED));

        LocalDateTime now = now();
        if (session.isExpired(now)) {
            // 만료 row를 여기서 지우지 않는다, 바로 아래 예외로 같은 트랜잭션이 롤백되면 삭제도 사라진다
            // 인증 정확성은 expires_at 판정만으로 보장되므로 row가 남아 있어도 인증되지 않는다
            throw new BusinessException(SESSION_EXPIRED);
        }
        if (session.needsExtension(now, authProperties.session().renewThreshold())) {
            session.extend(now, authProperties.session().ttl());
        }

        // LAZY 프록시의 식별자 getter는 프록시를 초기화하지 않으므로 users 조회가 추가로 나가지 않는다
        return new AuthenticatedUser(session.getUser().getId());
    }

    /** 없는 토큰이어도 예외를 던지지 않는다. 로그아웃은 멱등해야 한다. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        userSessionRepository.deleteById(sessionTokenGenerator.hash(rawToken));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
