package com.clinic.schedule.practitioner.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.practitioner.api.ReasonDetails;
import com.clinic.schedule.practitioner.api.TimeOffWriter;
import com.clinic.schedule.practitioner.entity.Practitioner;
import com.clinic.schedule.practitioner.entity.Reason;
import com.clinic.schedule.practitioner.entity.TimeOff;
import com.clinic.schedule.practitioner.entity.WorkingHours;
import com.clinic.schedule.practitioner.persistence.PractitionerRepository;

/**
 * What reception configures for each practitioner, and what other modules may read about
 * them.
 *
 * <p>It is public because the presentation layer lives in another package; other modules
 * still reach it only as a {@link PractitionerReader} or a {@link TimeOffWriter}, which
 * {@code ArchitectureTest} checks now that visibility no longer can (D-24, D-42).
 *
 * <p>Thin on purpose: the rules that need only the practitioner's own data live in
 * {@link Practitioner}, and this class loads, delegates and saves. Each method declares its
 * own transaction and every write saves explicitly (D-27).
 */
@Service
public class PractitionerService implements PractitionerReader, TimeOffWriter {

    private final PractitionerRepository practitioners;

    PractitionerService(PractitionerRepository practitioners) {
        this.practitioners = practitioners;
    }

    @Transactional
    public Practitioner register(String fullName) {
        return practitioners.save(Practitioner.register(fullName));
    }

    @Transactional
    public Practitioner rename(UUID id, String fullName) {
        Practitioner practitioner = load(id);
        practitioner.rename(fullName);
        return practitioners.save(practitioner);
    }

    @Transactional
    public Reason addReason(UUID id, String name, int durationMinutes) {
        Practitioner practitioner = load(id);
        Reason reason = practitioner.addReason(name, durationMinutes);
        practitioners.save(practitioner);
        return reason;
    }

    @Transactional
    public Reason changeReason(UUID id, UUID reasonId, String name, int durationMinutes, boolean webBookable) {
        Practitioner practitioner = load(id);
        Reason reason = practitioner.changeReason(reasonId, name, durationMinutes, webBookable);
        practitioners.save(practitioner);
        return reason;
    }

    @Transactional
    public Practitioner replaceWeeklySchedule(UUID id, List<WorkingHours> hours) {
        Practitioner practitioner = load(id);
        practitioner.replaceWeeklySchedule(hours);
        return practitioners.save(practitioner);
    }

    @Transactional(readOnly = true)
    public Practitioner byId(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<Practitioner> all() {
        return practitioners.findAllByOrderByFullNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PractitionerSummary> findAll() {
        return practitioners.findAllByOrderByFullNameAsc().stream()
                .map(practitioner -> new PractitionerSummary(
                        practitioner.getId(),
                        practitioner.getFullName(),
                        practitioner.getReasons().stream()
                                .map(PractitionerService::details)
                                .toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReasonDetails> findReason(UUID practitionerId, UUID reasonId) {
        return practitioners.findById(practitionerId)
                .flatMap(practitioner -> practitioner.findReason(reasonId))
                .map(PractitionerService::details);
    }

    private static ReasonDetails details(Reason reason) {
        return new ReasonDetails(reason.getId(), reason.getName(), reason.getDurationMinutes(),
                reason.isWebBookable());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean worksDuring(UUID practitionerId, LocalDateTime startsAt, LocalDateTime endsAt) {
        return practitioners.findById(practitionerId)
                .map(practitioner -> practitioner.worksDuring(startsAt, endsAt))
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean recordTimeOff(UUID practitionerId, LocalDateTime startsAt, LocalDateTime endsAt) {
        Optional<Practitioner> found = practitioners.findById(practitionerId);
        if (found.isEmpty()) {
            return false;
        }
        Practitioner practitioner = found.get();
        practitioner.recordTimeOff(new TimeOff(startsAt, endsAt));
        practitioners.save(practitioner);
        return true;
    }

    private Practitioner load(UUID id) {
        return practitioners.findById(id).orElseThrow(() -> new PractitionerNotFoundException(id));
    }
}
