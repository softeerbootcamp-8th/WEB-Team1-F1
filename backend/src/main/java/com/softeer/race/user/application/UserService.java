package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_USERNAME;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;
import static com.softeer.race.notification.domain.NotificationType.WELCOME;

import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.exception.ErrorCode;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.application.dto.info.SignUpInfo;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPublisher notificationPublisher;

    @Transactional
    public SignUpInfo signUp(SignUpCommand command) {
        if (!command.role().isSelfSignUpAllowed()) {
            throw new BusinessException(UNSUPPORTED_SIGNUP_ROLE);
        }
        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessException(DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new BusinessException(DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(
                command.username(),
                command.email(),
                encodedPassword,
                command.realName(),
                command.phone(),
                command.role());

        User savedUser = save(user);

        // 가입과 한 트랜잭션에 둔다. 따로 떼면 가입은 실패했는데 환영 알림만 남는 경우가 생기고,
        // 반대로 전달 실패는 가입을 흔들지 않는다 — 전달은 커밋 뒤 NotificationPusher 가 맡는다.
        // 가입은 세션을 발급하지 않아 이 시점에 구독이 없다. 전달될 곳이 없어도 실패가 아니고,
        // 다음 접속의 연결 직후 건수 한 번이 배지를 맞춘다
        notificationPublisher.publish(savedUser.getId(), WELCOME, null);

        return SignUpInfo.from(savedUser);
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
        return message != null && message.contains("uk_users_username")
                ? DUPLICATE_USERNAME
                : DUPLICATE_EMAIL;
    }
}
