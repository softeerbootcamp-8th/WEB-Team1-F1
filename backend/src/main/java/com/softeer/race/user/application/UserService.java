package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_PHONE;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_USERNAME;
import static com.softeer.race.user.exception.UserErrorCode.DEALER_LICENSE_NOT_ALLOWED;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.exception.ErrorCode;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.dealer.application.DealerApplicationService;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.application.dto.info.SignUpInfo;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DealerApplicationService dealerApplicationService;

    /**
     * 회원을 만든다. <b>딜러로 신청해도 만들어지는 회원은 일반 회원이다.</b>
     * <p>
     * 사원증을 낸 것과 딜러인 것은 다르다. 예전에는 가입이 곧 딜러 자격이라 아무도 검토하지 않은
     * 사원증으로 딜러가 됐다. 이제 가입은 심사 신청까지만 만들고, 자격은 관리자가 승인할 때 붙는다.
     * <p>
     * 사원증 검증이 중복 조회보다 뒤로 밀렸다. 신청 생성이 회원 저장 뒤라야 하기 때문인데
     * ({@code DealerApplicationService#apply}에 그 이유가 있다), 그래서 사원증이 빠진 딜러 가입은
     * 예전과 달리 아이디 중복이 먼저 걸린다. 어느 쪽이든 400·409로 거절되는 요청이라 그대로 둔다.
     */
    @Transactional
    public SignUpInfo signUp(SignUpCommand command) {
        if (!command.role().isSelfSignUpAllowed()) {
            throw new BusinessException(UNSUPPORTED_SIGNUP_ROLE);
        }
        boolean dealerApplicant = command.role() == Role.DEALER;
        if (!dealerApplicant && command.dealerLicenseKey() != null) {
            throw new BusinessException(DEALER_LICENSE_NOT_ALLOWED);
        }
        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessException(DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new BusinessException(DUPLICATE_EMAIL);
        }
        if (userRepository.existsByPhone(command.phone())) {
            throw new BusinessException(DUPLICATE_PHONE);
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(
                command.username(),
                command.email(),
                encodedPassword,
                command.realName(),
                command.phone(),
                dealerApplicant ? Role.GENERAL : command.role());

        User savedUser = save(user);

        if (!dealerApplicant) {
            return SignUpInfo.from(savedUser);
        }

        // 신청 실패는 가입까지 되돌린다. 같은 트랜잭션이라 저절로 그렇게 되고, 그래야 한다 —
        // 사원증을 내고 가입했는데 회원만 남고 신청이 없으면 그 사람은 심사를 기다리는 줄도 모른 채
        // 영영 대기하게 된다
        DealerApplicationInfo application =
                dealerApplicationService.apply(savedUser.getId(), command.dealerLicenseKey());

        return SignUpInfo.from(savedUser, application.status());
    }

    // 저장만 감싼다. 발행까지 이 try 안에 두면 알림 쪽 제약 위반이 아이디·이메일 중복으로 번역된다
    private User save(User user) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(resolveDuplicateErrorCode(exception));
        }
    }

    // 사전 검증과 저장 사이의 경합은 DB 제약이 잡아내므로, 위반된 제약명으로 원인을 구분한다
    private ErrorCode resolveDuplicateErrorCode(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uk_users_username")) {
            return DUPLICATE_USERNAME;
        }
        if (message != null && message.contains("uk_users_phone")) {
            return DUPLICATE_PHONE;
        }
        return DUPLICATE_EMAIL;
    }
}
