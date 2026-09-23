package com.clinic.schedule.practitioner;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What reception configures for each practitioner. Package-private, like the rest of the
 * module except what other modules will consume (D-24).
 *
 * <p>Thin on purpose: the rules that need only the practitioner's own data live in
 * {@link Practitioner}, and this class loads, delegates and saves. Each method declares its
 * own transaction and every write saves explicitly (D-27).
 */
@Service
class PractitionerService {

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

    private Practitioner load(UUID id) {
        return practitioners.findById(id).orElseThrow(() -> new PractitionerNotFoundException(id));
    }
}
