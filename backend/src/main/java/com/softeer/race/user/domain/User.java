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
//
// 인덱스는 관리자 회원 목록과 짝이다. 역할·이용 상태로 좁혀 가입 최신순으로 읽는다.
// 뒤에 id 를 적지 않는 이유는 dealer_application 과 같다 — InnoDB 의 보조 인덱스는 PK 를 뒤에
// 달고 있어 (role, status) 만으로 이미 (role, status, id) 순서다
//
// 검색어(keyword)는 이 인덱스를 타지 못한다. 아이디·이름·연락처를 OR 로 묶은 부분 일치라
// 어떤 인덱스도 쓸 수 없고 전체를 훑는다. 회원 수가 수만을 넘어가면 검색 전용 인덱스가 필요하다
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
        },
        indexes = @Index(name = "idx_users_role_status", columnList = "role, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    /**
     * 정지 사유의 상한. 컬럼 폭과 요청 검증이 이 한 값을 함께 본다.
     * <p>
     * 관리자가 왜 정지했는지 남기는 한두 문장이다. {@code DealerApplication.MAX_REJECT_REASON_LENGTH}와
     * 같은 값이고, 같은 이유로 넉넉히 잡지 않는다.
     */
    public static final int MAX_SUSPEND_REASON_LENGTH = 500;

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

    /**
     * 서비스 이용 상태. 역할과 직교해서 정지해도 원래 역할이 그대로 남고, 해제하면 그 역할로 돌아온다.
     * <p>
     * 컬럼 타입은 MySQL {@code ENUM}이 아니라 {@code VARCHAR}다({@code @Enumerated(STRING)}의 기본).
     * {@code ENUM}으로 두면 값을 하나 더할 때마다 운영 DB를 직접 {@code ALTER}해야 한다 —
     * {@code ddl-auto: update}는 이미 있는 컬럼의 타입을 바꿔주지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    /**
     * 정지 사유. 정지 상태가 아니면 비어 있다 — 그 null이 "정지되지 않았다"를 뜻한다.
     * <p>
     * 길이를 못 박는 이유는 {@code DealerApplication.rejectReason}과 같다. 기본값(255)에 기대면
     * 컬럼 폭이 요청 검증과 따로 놀아, 검증 상한을 늘리는 순간 저장에서 잘리거나 터진다.
     * <p>
     * <b>마지막 한 건만 남는다.</b> 재정지되면 이전 사유는 덮인다. 정지·해제 이력이 필요해지면
     * 별도 테이블로 옮겨야 하고, 그때 이 컬럼은 그 이력의 파생값이 된다.
     */
    @Column(length = MAX_SUSPEND_REASON_LENGTH)
    private String suspendReason;

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
        this.status = UserStatus.ACTIVE;
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

    /**
     * 정지할 수 있는 회원인지. {@link #suspend}와 나눠 두는 이유는 {@code DealerApplication}과 같다 —
     * 검증하는 메서드가 상태까지 바꾸면 이름이 거짓이 되고, 바꾸는 메서드 안에서 다시 검사하면
     * 어느 쪽이 진짜 관문인지 흐려진다.
     * <p>
     * 관리자와 평가사는 여기서 걸린다({@code Role.isSuspendable}). 관리자를 막는 것이 곧
     * <b>자기 자신을 정지할 수 없다</b>는 보장이다 — 이 경로는 {@code /api/admin/**} 뒤에 있어
     * 요청자가 언제나 관리자이기 때문이다.
     */
    public void validateSuspendable() {
        if (!role.isSuspendable()) {
            throw new BusinessException(UserErrorCode.NOT_SUSPENDABLE_ROLE);
        }
        if (status == UserStatus.SUSPENDED) {
            throw new BusinessException(UserErrorCode.ALREADY_SUSPENDED);
        }
    }

    /** 해제할 수 있는 회원인지. 정지되지 않은 회원을 해제하는 것은 관리자가 목록을 잘못 본 것이다. */
    public void validateActivatable() {
        if (status == UserStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.ALREADY_ACTIVE);
        }
    }

    /**
     * 서비스 이용을 정지한다. 역할은 건드리지 않는다 — 해제할 때 되돌릴 값이 없어지기 때문이다.
     * <p>
     * <b>세션은 여기서 끊기지 않는다.</b> 인증은 로그인 시점에 복사된 세션 하나로 끝나므로,
     * 부르는 쪽이 그 회원의 세션을 함께 폐기해야 정지가 지금 접속 중인 사람에게도 듣는다
     * ({@code UserSuspensionService.suspend}).
     */
    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.suspendReason = reason;
    }

    /** 이용을 다시 연다. 사유를 지워, 남아 있는 사유가 곧 지금 정지 중이라는 뜻이 되게 한다. */
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.suspendReason = null;
    }

    /** 로그인을 막을지 판정하는 값. 인증을 통과한 뒤에는 언제나 false다({@link UserStatus}). */
    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    // 이름은 가운데를 가려 내보내므로 두 글자보다 짧으면 가릴 자리가 없다
    // 여기서 막지 않으면 호가창을 읽는 시점에 터져 그 사람이 입찰한 방 전체가 응답못함
    private static void validateRealName(String realName) {
        if (realName == null || realName.strip().length() < MIN_REAL_NAME_LENGTH) {
            throw new BusinessException(UserErrorCode.INVALID_REAL_NAME);
        }
    }
}
