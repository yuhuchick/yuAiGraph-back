package com.knowledge.repository;

import com.knowledge.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRole(String role);
    List<User> findTop10ByOrderByCreatedAtDesc();

    @Query("""
        SELECT u FROM User u WHERE
        (:kw IS NULL OR TRIM(:kw) = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :kw, '%'))
         OR LOWER(u.email) LIKE LOWER(CONCAT('%', :kw, '%')))
        AND (:role IS NULL OR TRIM(:role) = '' OR COALESCE(u.role, 'USER') = :role)
        """)
    Page<User> searchUsers(@Param("kw") String keyword, @Param("role") String role, Pageable pageable);
}
