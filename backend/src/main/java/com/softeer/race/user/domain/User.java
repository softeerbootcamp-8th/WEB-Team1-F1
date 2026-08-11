package com.softeer.race.user.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        @UniqueConstraint(name = "uk_users_dealer_license_key", columnNames = "dealer_license_key")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

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

    // 외부 조회 URL이 아니라 비공개 S3 객체 키만 저장한다. 일반 회원과 기존 딜러는 null일 수 있다.
    @Column(name = "dealer_license_key", length = 255)
    private String dealerLicenseKey;

    private User(
            String username,
            String email,
            String encodedPassword,
            String realName,
            String phone,
            Role role,
            String dealerLicenseKey) {
        this.username = username;
        this.email = email;
        this.password = encodedPassword;
        this.realName = realName;
        this.phone = phone;
        this.role = role;
        this.dealerLicenseKey = dealerLicenseKey;
    }

    public static User create(
            String username,
            String email,
            String encodedPassword,
            String realName,
            String phone,
            Role role) {
        return create(username, email, encodedPassword, realName, phone, role, null);
    }

    public static User create(
            String username,
            String email,
            String encodedPassword,
            String realName,
            String phone,
            Role role,
            String dealerLicenseKey) {
        return new User(username, email, encodedPassword, realName, phone, role, dealerLicenseKey);
    }
}
