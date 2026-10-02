package com.clinic.schedule.reminder.application.port;

import java.util.Map;
import java.util.UUID;

/** The practitioners' names, asked of the practitioner module, for the reminders to say who the visit is with. */
public interface PractitionerNamesPort {

    Map<UUID, String> names();
}
