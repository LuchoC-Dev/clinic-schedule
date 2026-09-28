package com.clinic.schedule.user.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Only active users can log in; an inactive one is as good as unknown. */
    Optional<AppUser> findByUsernameAndActiveTrue(String username);

    /** Whether the username was ever used, by an active user or not: a username is never used twice. */
    boolean existsByUsername(String username);

    boolean existsByPractitionerIdAndActiveTrue(UUID practitionerId);

    List<AppUser> findAllByOrderByUsernameAsc();
}
