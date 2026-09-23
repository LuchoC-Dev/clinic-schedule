package com.clinic.schedule.practitioner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.ReasonDetails;

/**
 * What reception configures for each practitioner, and what other modules may read about
 * them.
 *
 * <p>The class is package-private, so other modules reach it only as a
 * {@link PractitionerReader}: the two methods of that interface are public because the
 * interface requires it, and everything else stays inside the module (D-24).
 *
 * <p>Thin on purpose: the rules that need only the practitioner's own data live in
 * {@link Practitioner}, and this class loads, delegates and saves. Each method declares its
 * own transaction and every write saves explicitly (D-27).
 */
@Service
class PractitionerService implements PractitionerReader {

    private final PractitionerRepository practitioners;

    PractitionerService(PractitionerRepository practitioners) {
        this.practitioners = practitioners;
    }

    @Transactional
    Practitioner register(String fullName) {
        return practitioners.save(Practitioner.register(fullName));
    }

    @Transactional
    Practitioner rename(UUID id, String fullName) {
        Practitioner practitioner = load(id);
        practitioner.rename(fullName);
        return practitioners.save(practitioner);
    }

    @Transactional
    Reason addReason(UUID id, String name, int durationMinutes) {
        Practitioner practitioner = load(id);
        Reason reason = practitioner.addReason(name, durationMinutes);
        practitioners.save(practitioner);
        return reason;
    }

    @Transactional
    Reason changeReason(UUID id, UUID reasonId, String name, int durationMinutes) {
        Practitioner practitioner = load(id);
        Reason reason = practitioner.changeReason(reasonId, name, durationMinutes);
        practitioners.save(practitioner);
        return reason;
    }

    @Transactional
    Practitioner replaceWeeklySchedule(UUID id, List<WorkingHours> hours) {
        Practitioner practitioner = load(id);
        practitioner.replaceWeeklySchedule(hours);
        return practitioners.save(practitioner);
    }

    @Transactional(readOnly = true)
    Practitioner byId(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    List<Practitioner> all() {
        return practitioners.findAllByOrderByFullNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReasonDetails> findReason(UUID practitionerId, UUID reasonId) {
        return practitioners.findById(practitionerId)
                .flatMap(practitioner -> practitioner.findReason(reasonId))
                .map(reason -> new ReasonDetails(reason.getId(), reason.getName(), reason.getDurationMinutes()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean worksDuring(UUID practitionerId, LocalDateTime startsAt, LocalDateTime endsAt) {
        return practitioners.findById(practitionerId)
                .map(practitioner -> practitioner.worksDuring(startsAt, endsAt))
                .orElse(false);
    }

    private Practitioner load(UUID id) {
        return practitioners.findById(id).orElseThrow(() -> new PractitionerNotFoundException(id));
    }
}
