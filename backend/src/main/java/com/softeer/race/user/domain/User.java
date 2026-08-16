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

    /**
     * 딜러 자격을 붙인다. 심사를 통과한 신청만 이 메서드를 부른다({@code DealerApplication.approve}).
     * <p>
     * setter가 아니라 이름 있는 메서드인 이유는 역할이 아무 데서나 바뀌지 않게 하려는 것이다.
     * 관리자·평가사로 올리는 경로는 없다 — 그 둘은 서비스가 직접 정하는 자리라 심사가 없다.
     * <p>
     * <b>세션에는 반영되지 않는다.</b> 역할이 로그인 시점에 세션으로 복사되므로
     * ({@code AuthenticatedUser}) 이 회원은 다시 로그인하거나 세션이 만료될 때까지 일반 회원으로
     * 동작한다. 그 세션을 폐기하는 일은 별도 이슈로 다룬다.
     */
    public void promoteToDealer() {
        this.role = Role.DEALER;
    }

    // 이름은 가운데를 가려 내보내므로 두 글자보다 짧으면 가릴 자리가 없다
    // 여기서 막지 않으면 호가창을 읽는 시점에 터져 그 사람이 입찰한 방 전체가 응답못함
    private static void validateRealName(String realName) {
        if (realName == null || realName.strip().length() < MIN_REAL_NAME_LENGTH) {
            throw new BusinessException(UserErrorCode.INVALID_REAL_NAME);
        }
    }
}
