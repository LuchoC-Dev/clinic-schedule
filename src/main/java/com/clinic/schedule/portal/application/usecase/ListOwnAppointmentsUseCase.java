package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientAppointmentsPort;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.OwnAppointments;

/**
 * The appointments of the patient logged in, as {@link OwnAppointments} lists them (D-80), and those of the
 * members of their family they act for (D-85). Whose they are is never asked: it is whoever is logged in and
 * their family, so no patient can ask for another's.
 */
@Service
public class ListOwnAppointmentsUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientAppointmentsPort appointments;
    private final ViewFamilyUseCase family;
    private final Clock clock;

    public ListOwnAppointmentsUseCase(CurrentPatientPort currentPatient, PatientAppointmentsPort appointments,
            ViewFamilyUseCase family, Clock clock) {
        this.currentPatient = currentPatient;
        this.appointments = appointments;
        this.family = family;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OwnAppointments handle() {
        return OwnAppointments.of(appointments.of(currentPatient.patientId()), LocalDateTime.now(clock));
    }

    /** Those of each member the patient acts for (D-85), by name, read as the patient reads their own. */
    @Transactional(readOnly = true)
    public List<MemberAppointments> family() {
        LocalDateTime now = LocalDateTime.now(clock);
        return family.actedFor().stream()
                .map(member -> new MemberAppointments(member, OwnAppointments.of(appointments.of(member.patientId()),
                        now)))
                .toList();
    }

    /** A member's appointments. */
    public record MemberAppointments(AccountHolder member, OwnAppointments appointments) {
    }
}
