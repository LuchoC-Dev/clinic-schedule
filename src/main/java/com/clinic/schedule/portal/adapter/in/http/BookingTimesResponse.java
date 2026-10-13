package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * The times offered on a day for a reason booked on the web (D-82), or to move an appointment to (D-93), as hours
 * and minutes.
 */
record BookingTimesResponse(LocalDate date, @JsonFormat(pattern = "HH:mm") List<LocalTime> times) {
}
