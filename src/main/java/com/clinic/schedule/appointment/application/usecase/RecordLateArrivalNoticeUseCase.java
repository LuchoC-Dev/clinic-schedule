package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * The patient called to say they will be late: the appointment waits until the end of its day,
 * instead of 30 minutes, before the patient is absent (D-06, D-61). Only on the day of the
 * appointment (D-38).
 */
@Service
public class RecordLateArrivalNoticeUseCase {

    private final AppointmentRepository appointments;
    private final Clock clock;

    public RecordLateArrivalNoticeUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional
    public Appointment handle(AppointmentId id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));

        Appointment warned = appointment.withLateArrivalNotice(LocalDateTime.now(clock));
        appointments.save(warned);
        return warned;
    }
}
