package com.softeer.race.user.presentation;

import com.softeer.race.user.application.UserService;
import com.softeer.race.user.application.dto.info.SignUpInfo;
import com.softeer.race.user.presentation.request.SignUpRequest;
import com.softeer.race.user.presentation.response.SignUpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "회원 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "일반 회원 또는 딜러 회원을 생성합니다.")
    @PostMapping
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpInfo info = userService.signUp(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(SignUpResponse.from(info));
    }
}
