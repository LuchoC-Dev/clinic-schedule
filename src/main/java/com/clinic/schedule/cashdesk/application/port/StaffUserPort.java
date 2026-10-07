package com.clinic.schedule.cashdesk.application.port;

/**
 * Who is using the application, to record who charged a visit (D-87). A {@code Port}: it is another
 * module, not storage (D-18).
 */
public interface StaffUserPort {

    String username();
}
