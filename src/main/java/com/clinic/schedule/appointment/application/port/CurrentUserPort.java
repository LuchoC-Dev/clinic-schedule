package com.clinic.schedule.appointment.application.port;

/**
 * Who is using the application, to record who cancelled an appointment (D-76). A {@code Port}: it is
 * another module, not storage (D-18).
 */
public interface CurrentUserPort {

    String username();
}
