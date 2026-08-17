package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.NOT_FOUND;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.application.dto.command.SuspendUserCommand;
import com.softeer.race.user.application.dto.info.UserStatusInfo;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 이용정지 유스케이스.
 * <p>
 * 가입을 다루는 {@link UserService}와 나눠 둔다. 부르는 사람도(가입자 · 관리자), 지켜야 할 규칙도
 * (중복 가입 차단 · 재정지 차단) 겹치지 않아서, 한 클래스에 두면 두 흐름의 검증이 서로의 맥락을
 * 모른 채 섞인다({@code DealerApplicationService}와 {@code DealerApplicationReviewService}가 갈린 이유와 같다).
 * <p>
 * <b>이 서비스가 세우는 불변식은 "정지된 회원에게는 살아 있는 세션이 없다"다.</b> 정지가 세션을
 * 전부 끊고 {@code AuthService.login}이 다시 들어오는 문을 막으므로, 인증을 통과한 요청의 주체는
 * 언제나 활성 회원이다. 그래서 인터셉터가 요청마다 이용 상태를 다시 읽지 않아도 되고,
 * 세션 값에 상태를 실을 필요도 없다({@code AuthenticatedUser}).
 */
@Service
@RequiredArgsConstructor
public class UserSuspensionService {

    private final UserRepository userRepository;
    private final SessionService sessionService;

    /**
     * 이용을 정지하고 그 회원의 세션을 함께 끊는다.
     * <p>
     * <b>세션 폐기를 함께 하지 않으면 정지가 지금 접속 중인 사람에게 듣지 않는다.</b> 인증이 로그인
     * 시점에 복사된 세션 하나로 끝나므로, 끊지 않으면 최대 세션 TTL 만큼 정지 전 그대로 이용한다 —
     * 악성 이용을 막으려고 누른 버튼이 정작 지금 이용 중인 사람에게만 안 듣는 것이 된다.
     * <p>
     * 폐기를 트랜잭션 안에서 부른다. 롤백됐는데 폐기만 남으면 그 회원이 한 번 더 로그인하면 그만이지만,
     * 커밋됐는데 폐기가 빠지면 위의 어긋난 상태가 그대로 남는다
     * ({@code DealerApplicationReviewService.approve}와 같은 판단이다).
     */
    @Transactional
    public UserStatusInfo suspend(SuspendUserCommand command) {
        User user = findUser(command.userId());

        user.validateSuspendable();
        user.suspend(command.reason());

        sessionService.revokeAllOf(user.getId());

        return UserStatusInfo.from(user);
    }

    /**
     * 이용을 다시 연다. <b>세션을 건드리지 않는다</b> — 정지된 회원에게는 살아 있는 세션이 없다는 것이
     * 이 서비스의 불변식이라 끊을 것이 없고, 당사자는 다시 로그인해 들어온다.
     */
    @Transactional
    public UserStatusInfo activate(Long userId) {
        User user = findUser(userId);

        user.validateActivatable();
        user.activate();

        return UserStatusInfo.from(user);
    }

    /**
     * 잠그지 않고 읽는다. 잠가도 얻는 것이 없어서다 — 같은 회원 행을 함께 건드리는 딜러 승인
     * ({@code DealerApplicationReviewService.approve})이 신청 행만 잠그고 회원 행은 잠그지 않으므로,
     * 여기서만 {@code FOR UPDATE}를 걸어도 그 경로와는 배타가 되지 않는다.
     * <p>
     * 관리자 둘이 같은 회원을 동시에 누르는 경우는 재정지 차단({@code validateSuspendable})이
     * 대부분 걸러내고, 통과한 나머지는 사유가 나중 것으로 덮이는 정도라 감수한다.
     */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND));
    }
}
