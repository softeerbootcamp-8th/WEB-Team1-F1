package com.softeer.race.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    /** 인증 주체의 최신 역할까지 한 쿼리로 읽는다. */
    @Query("""
            select session
            from UserSession session
            join fetch session.user
            where session.id = :id
            """)
    Optional<UserSession> findByIdWithUser(@Param("id") String id);
}
