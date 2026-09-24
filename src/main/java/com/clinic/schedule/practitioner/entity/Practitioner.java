package com.clinic.schedule.practitioner.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A practitioner with what reception configures for them: the reasons an appointment can be
 * requested for, the usual week of work, and the time off that interrupts it.
 *
 * <p>JPA maps it directly (D-25). It guards what only needs its own data: two reasons of the
 * same practitioner cannot share a name, and a week cannot have overlapping stretches. Those
 * rules live here and not in the service.
 *
 * <p>Both collections are fetched eagerly. They are tiny, a handful of reasons and a dozen
 * stretches, and every use of a practitioner needs them, so loading them lazily would only
 * add the risk of reading them after the transaction closed. The working hours are a
 * {@code Set} so Hibernate can fetch both collections at once; a duplicate stretch is
 * rejected by {@link WeeklySchedule} before it gets here anyway.
 */
@Entity
@Table(name = "practitioner")
public class Practitioner {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "practitioner_id", nullable = false)
    @OrderBy("name")
    private List<Reason> reasons = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "working_hours", joinColumns = @JoinColumn(name = "practitioner_id"))
    private Set<WorkingHours> workingHours = new HashSet<>();

    /** Also a {@code Set}, for the same reason as the working hours. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "time_off", joinColumns = @JoinColumn(name = "practitioner_id"))
    private Set<TimeOff> timeOff = new HashSet<>();

    protected Practitioner() {
        // required by JPA
    }

    private Practitioner(UUID id, String fullName) {
        this.id = id;
        this.fullName = requireName(fullName);
    }

    /** A new practitioner, with no reasons and no hours yet. The id is assigned here (D-19). */
    public static Practitioner register(String fullName) {
        return new Practitioner(UUID.randomUUID(), fullName);
    }

    public void rename(String fullName) {
        this.fullName = requireName(fullName);
    }

    /** @throws DuplicateReasonException when the practitioner already has a reason so named */
    public Reason addReason(String name, int durationMinutes) {
        requireNameIsFree(name, null);
        Reason reason = Reason.create(name, durationMinutes);
        reasons.add(reason);
        return reason;
    }

    /**
     * Renames a reason or changes its duration. Appointments already booked keep the duration
     * they were booked with, so the agenda does not move under reception's feet.
     */
    public Reason changeReason(UUID reasonId, String name, int durationMinutes) {
        Reason reason = findReason(reasonId).orElseThrow(() -> new ReasonNotFoundException(id, reasonId));
        requireNameIsFree(name, reason);
        reason.change(name, durationMinutes);
        return reason;
    }

    /**
     * Replaces the whole week. A schedule is edited as a table, so it is sent whole rather
     * than stretch by stretch.
     *
     * @throws InvalidWeeklyScheduleException when two stretches of a day overlap or meet
     */
    public void replaceWeeklySchedule(List<WorkingHours> hours) {
        WeeklySchedule schedule = new WeeklySchedule(hours);
        workingHours.clear();
        workingHours.addAll(schedule.hours());
    }

    public Optional<Reason> findReason(UUID reasonId) {
        return reasons.stream().filter(reason -> reason.getId().equals(reasonId)).findFirst();
    }

    /**
     * Records an absence. Overlapping absences are accepted: recording the same afternoon
     * twice, or a morning inside a whole day already off, leaves the practitioner just as
     * absent, so there is nothing to refuse.
     */
    public void recordTimeOff(TimeOff absence) {
        timeOff.add(absence);
    }

    /** Whether the whole span falls inside one stretch of the usual week and outside any time off. */
    public boolean worksDuring(LocalDateTime startsAt, LocalDateTime endsAt) {
        return new AvailabilityPolicy(new WeeklySchedule(getWorkingHours()), timeOff).isAvailable(startsAt, endsAt);
    }

    private void requireNameIsFree(String name, Reason except) {
        for (Reason reason : reasons) {
            if (reason != except && reason.isNamed(name)) {
                throw new DuplicateReasonException(name);
            }
        }
    }

    /** Fits its column: the screen reaches this without the API's Bean Validation (D-38, D-46). */
    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("A practitioner needs a full name");
        }
        String name = fullName.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A full name is at most " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    /** As long as the column that stores it. */
    static final int MAX_NAME_LENGTH = 150;

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public List<Reason> getReasons() {
        return List.copyOf(reasons);
    }

    /** In the order they happen. */
    public List<TimeOff> getTimeOff() {
        return timeOff.stream().sorted(Comparator.comparing(TimeOff::startsAt)).toList();
    }

    /** In the order a week is read: by day, then by start time. */
    public List<WorkingHours> getWorkingHours() {
        return workingHours.stream()
                .sorted(Comparator.comparing(WorkingHours::day).thenComparing(WorkingHours::startsAt))
                .toList();
    }
}
