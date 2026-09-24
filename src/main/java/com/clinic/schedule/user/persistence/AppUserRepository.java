package com.clinic.schedule.user.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Only active users can log in; an inactive one is as good as unknown. */
    Optional<AppUser> findByUsernameAndActiveTrue(String username);
}
