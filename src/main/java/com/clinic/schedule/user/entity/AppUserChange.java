package com.clinic.schedule.user.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What an admin did to a user, or the user to their own password, by whom and when (D-68, D-69).
 * Only {@link AppUser} makes one, and nothing changes one once made.
 */
@Entity
@Table(name = "app_user_change")
public class AppUserChange {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserAction action;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected AppUserChange() {
        // required by JPA
    }

    AppUserChange(UUID userId, UserAction action, LocalDateTime changedAt, String changedBy) {
        if (changedAt == null || changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("A change to a user needs when and by whom");
        }
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.action = action;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UserAction getAction() {
        return action;
    }

    /** The username of the admin who did it. */
    public String getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
