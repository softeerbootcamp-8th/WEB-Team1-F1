package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_USERNAME;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;

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
