package com.clinic.schedule.appointment.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Records a practitioner's time off and handles the appointments it hits (D-32):
 *
 * <ul>
 *   <li>those whose patient has not arrived are cancelled automatically;</li>
 *   <li>those whose patient already arrived are left as they are and reported, because a
 *       person is waiting and reception has to decide what to do.</li>
 * </ul>
 *
 * <p>It lives in this module and not in the practitioner one because the consequences are
 * about appointments, and the practitioner module does not know them. The absence is stored
 * through the practitioner's api inside this same transaction, so the absence and its
 * consequences are stored together or not at all.
 *
 * <p>Future booking during the absence is refused by the availability check, like any time
 * outside the practitioner's hours.
 */
@Service
public class RecordTimeOffUseCase {

    private final AppointmentRepository appointments;
    private final PractitionerSchedulePort practitioners;

    public RecordTimeOffUseCase(AppointmentRepository appointments, PractitionerSchedulePort practitioners) {
        this.appointments = appointments;
        this.practitioners = practitioners;
    }

    @Transactional
    public Result handle(Command command) {
        if (!practitioners.recordTimeOff(command.practitionerId(), command.span())) {
            throw new UnknownPractitionerException(command.practitionerId());
        }

        List<Appointment> hit = bookedDuring(command.practitionerId(), command.span());

        List<Appointment> cancelled = hit.stream()
                .filter(appointment -> !appointment.isCheckedIn())
                .map(Appointment::cancelled)
                .toList();
        cancelled.forEach(appointments::save);

        List<Appointment> needsManualHandling = hit.stream().filter(Appointment::isCheckedIn).toList();

        return new Result(cancelled, needsManualHandling);
    }

    /** Every day the span touches is read whole, and the overlap decided on each appointment. */
    private List<Appointment> bookedDuring(PractitionerId practitionerId, TimeSlot span) {
        List<Appointment> hit = new ArrayList<>();
        for (LocalDate day = span.startsAt().toLocalDate();
                day.atStartOfDay().isBefore(span.endsAt());
                day = day.plusDays(1)) {
            for (Appointment appointment : appointments.findBookedForPractitionerOn(practitionerId, day)) {
                if (appointment.slot().overlaps(span)) {
                    hit.add(appointment);
                }
            }
        }
        return hit;
    }

    /** The practitioner and the span they are absent for: a whole day, or part of one. */
    public record Command(PractitionerId practitionerId, TimeSlot span) {
    }

    /**
     * What the absence did: the appointments it cancelled, and the ones it could not because
     * the patient is already there.
     */
    public record Result(List<Appointment> cancelled, List<Appointment> needsManualHandling) {
    }
}
