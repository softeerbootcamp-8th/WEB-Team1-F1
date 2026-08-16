package com.softeer.race.user.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.exception.UserErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
// @Column(unique = true)는 제약명이 자동 생성되어 위반 시 어떤 컬럼인지 구분할 수 없다
// UserService가 제약명으로 중복 원인을 가려내므로 이름을 직접 지정한다
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    // MaskedName 이 가릴 수 있는 최소 길이와 같다
    private static final int MIN_REAL_NAME_LENGTH = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String realName;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private User(
            String username,
            String email,
            String encodedPassword,
            String realName,
            String phone,
            Role role) {
        this.username = username;
        this.email = email;
        this.password = encodedPassword;
        this.realName = realName;
        this.phone = phone;
        this.role = role;
    }

    public static User create(
            String username,
            String email,
            String encodedPassword,
            String realName,
            String phone,
            Role role) {
        validateRealName(realName);

        return new User(username, email, encodedPassword, realName, phone, role);
    }

    // 이름은 가운데를 가려 내보내므로 두 글자보다 짧으면 가릴 자리가 없다
    // 여기서 막지 않으면 호가창을 읽는 시점에 터져 그 사람이 입찰한 방 전체가 응답못함
    private static void validateRealName(String realName) {
        if (realName == null || realName.strip().length() < MIN_REAL_NAME_LENGTH) {
            throw new BusinessException(UserErrorCode.INVALID_REAL_NAME);
        }
    }
}
