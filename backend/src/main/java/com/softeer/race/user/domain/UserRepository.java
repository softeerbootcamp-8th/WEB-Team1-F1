package com.softeer.race.user.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // 알림 발행이 식별자만 받으므로 엔티티를 읽지 않는다
    @Query("select u.id from User u where u.role = :role")
    List<Long> findIdsByRole(@Param("role") Role role);
}
