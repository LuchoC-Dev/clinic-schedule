package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.UnknownReasonException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.application.port.PractitionerSchedulePort.BookableReason;
import com.clinic.schedule.appointment.application.port.PractitionerSchedulePort;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Start times worth offering reception on a day: every quarter of an hour where the
 * practitioner works and a slot of the given length is free.
 *
 * <p>A suggestion, not a grid. The context says the interface may suggest intervals without
 * imposing a rigid grid on the domain, so booking still accepts any start time; this only
 * saves reception from guessing (D-33).
 *
 * <p>It considers the practitioner's agenda, not the patient's: the patient may not have been
 * chosen yet, and booking checks the patient anyway.
 */
@Service
public class SuggestSlotsUseCase {

    static final Duration STEP = Duration.ofMinutes(15);

    private final AppointmentRepository appointments;
    private final PractitionerSchedulePort practitioners;
    private final Clock clock;

    public SuggestSlotsUseCase(AppointmentRepository appointments, PractitionerSchedulePort practitioners,
            Clock clock) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.clock = clock;
    }

    /** For a new appointment: the length follows from the reason, as when booking. */
    @Transactional(readOnly = true)
    public List<LocalTime> forReason(PractitionerId practitionerId, UUID reasonId, LocalDate date) {
        BookableReason reason = practitioners.findReason(practitionerId, reasonId)
                .orElseThrow(() -> new UnknownReasonException(practitionerId, reasonId));
        return free(practitionerId, Duration.ofMinutes(reason.durationMinutes()), date, null);
    }

    /**
     * For moving an appointment: its booked length, and its own slot does not count as taken,
     * so moving it by a quarter of an hour is offered.
     */
    @Transactional(readOnly = true)
    public List<LocalTime> forMoving(Appointment appointment, LocalDate date) {
        return free(appointment.practitionerId(), appointment.slot().duration(), date, appointment.id());
    }

    private List<LocalTime> free(PractitionerId practitionerId, Duration length, LocalDate date, AppointmentId ignoring) {
        List<Appointment> agenda = appointments.findBookedForPractitionerOn(practitionerId, date).stream()
                .filter(appointment -> !appointment.id().equals(ignoring))
                .toList();

        // A time already gone cannot be booked (D-38), so it is not offered either.
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalTime> free = new ArrayList<>();
        for (LocalDateTime start = date.atStartOfDay(); start.toLocalDate().equals(date); start = start.plus(STEP)) {
            TimeSlot slot = new TimeSlot(start, length);
            if (!start.isBefore(now)
                    && practitioners.worksDuring(practitionerId, slot)
                    && agenda.stream().noneMatch(appointment -> appointment.slot().overlaps(slot))) {
                free.add(start.toLocalTime());
            }
        }
        return free;
    }
}
