package com.linkride.backend.repository;

import com.linkride.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByCollegeEmail(String collegeEmail);

    /** Devtools only: finds every seeded user by their {@code @<seedEmailDomain>} marker suffix, for {@code reset()}. */
    List<User> findByCollegeEmailEndingWith(String suffix);
}