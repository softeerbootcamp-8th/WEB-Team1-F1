package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.infrastructure.UserRepository;
import com.softeer.race.user.presentation.dto.request.SignUpRequest;
import com.softeer.race.user.presentation.dto.response.SignUpResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (!request.role().isSelfSignUpAllowed()) {
            throw new BusinessException(UNSUPPORTED_SIGNUP_ROLE);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.create(
                request.email(),
                encodedPassword,
                request.nickname(),
                request.phone(),
                request.address(),
                request.role());

        try {
            User savedUser = userRepository.save(user);
            return SignUpResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(DUPLICATE_EMAIL);
        }
    }
}
