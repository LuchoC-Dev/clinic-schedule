package com.clinic.schedule.practitioner;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Something an appointment can be requested for, such as "consulta" or "control", with the
 * time it takes for this practitioner. The same reason may take a different time with
 * another practitioner, which is why it belongs to one.
 *
 * <p>It has its own id so it can be renamed without losing its identity. The duration is
 * what reception reserves in the agenda; the real visit may run longer (see the context).
 */
@Entity
@Table(name = "reason")
class Reason {

    private static final int MAX_NAME_LENGTH = 60;

    /** Nothing booked lasts longer than a working day; a larger number is a mistyped one (D-38). */
    private static final int MAX_DURATION_MINUTES = 8 * 60;

    @Id
    private UUID id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    protected Reason() {
        // required by JPA
    }

    private Reason(UUID id, String name, int durationMinutes) {
        this.id = id;
        this.name = requireName(name);
        this.durationMinutes = requireDuration(durationMinutes);
    }

    static Reason create(String name, int durationMinutes) {
        return new Reason(UUID.randomUUID(), name, durationMinutes);
    }

    void change(String name, int durationMinutes) {
        this.name = requireName(name);
        this.durationMinutes = requireDuration(durationMinutes);
    }

    /** Names are compared ignoring case and surrounding spaces: "Consulta" is "consulta ". */
    boolean isNamed(String other) {
        return other != null && name.equalsIgnoreCase(other.strip());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A reason needs a name");
        }
        String stripped = name.strip();
        if (stripped.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A reason name is at most " + MAX_NAME_LENGTH + " characters");
        }
        return stripped;
    }

    private static int requireDuration(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("A reason needs a positive duration");
        }
        if (durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("A reason lasts at most " + MAX_DURATION_MINUTES + " minutes");
        }
        return durationMinutes;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    int getDurationMinutes() {
        return durationMinutes;
    }
}
