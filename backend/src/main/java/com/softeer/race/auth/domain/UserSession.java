package com.softeer.race.auth.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서버가 발급한 세션. 쿠키로 나가는 값은 원문이고 여기 남는 것은 그 SHA-256 해시다.
 * 세션 토큰은 비밀번호와 동급 credential이라, DB가 유출돼도 그것만으로는 세션을 탈취할 수 없어야 한다.
 * <p>
 * PK가 애플리케이션 생성값이므로 {@code save()}는 {@code persist}가 아니라 {@code merge}를 타고
 * SELECT 한 번을 더 쓴다. 로그인 경로에서만 발생하는 비용이라 수용한다.
 */
@Getter
@Entity
@Table(name = "user_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession extends BaseTimeEntity {

    private static final int HASHED_TOKEN_LENGTH = 64;

    /** 세션 토큰 원문의 SHA-256 hex 64자 */
    @Id
    @Column(length = HASHED_TOKEN_LENGTH)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private UserSession(String hashedToken, User user, LocalDateTime expiresAt) {
        this.id = hashedToken;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    // createdAt이 발급 시각, updatedAt이 마지막 연장 시각이라 별도 lastAccessedAt을 두지 않는다
    public static UserSession issue(String hashedToken, User user, LocalDateTime now, Duration ttl) {
        return new UserSession(hashedToken, user, now.plus(ttl));
    }

    /** 만료 시각에 정확히 도달한 순간부터 만료로 본다 */
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 남은 유효 시간이 임계값 이하인지. 매 요청 UPDATE를 피하기 위한 판정
     */
    public boolean needsExtension(LocalDateTime now, Duration threshold) {
        return !expiresAt.isAfter(now.plus(threshold));
    }

    /** 남은 시간에 더하지 않고 현재 시각 기준 절대값으로 다시 잡는다 */
    public void extend(LocalDateTime now, Duration ttl) {
        this.expiresAt = now.plus(ttl);
    }
}
