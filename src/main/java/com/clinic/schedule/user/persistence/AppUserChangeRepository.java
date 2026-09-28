package com.clinic.schedule.user.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.user.entity.AppUserChange;

/** What admins did to each user (D-68): only added to, and read on the user's screen. */
public interface AppUserChangeRepository extends JpaRepository<AppUserChange, UUID> {

    List<AppUserChange> findByUserIdOrderByChangedAtDesc(UUID userId);
}
