package com.clinic.schedule.reminder.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.reminder.domain.Contact;

/** How to reach each patient, asked of the patient module (D-78). */
public interface PatientContactPort {

    /** By patient; an unknown patient is left out. */
    Map<UUID, Contact> contactsOf(Collection<UUID> patientIds);
}
