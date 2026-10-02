package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.BookableReasonsPort;
import com.clinic.schedule.portal.application.port.BookingPort;
import com.clinic.schedule.portal.domain.BookableReason;
import com.clinic.schedule.portal.domain.WebBookingPolicy;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException.Reason;

/**
 * What a patient may book on the web (D-82): the reasons reception lets them, the days from tomorrow to
 * {@code app.portal.booking-days-ahead} days on, and the times the agenda offers on one of them.
 */
@Service
public class ViewWebBookingUseCase {

    private final BookableReasonsPort reasons;
    private final BookingPort booking;
    private final Clock clock;
    private final int daysAhead;

    public ViewWebBookingUseCase(BookableReasonsPort reasons, BookingPort booking, Clock clock,
            @Value("${app.portal.booking-days-ahead}") int daysAhead) {
        if (daysAhead < 1) {
            throw new IllegalArgumentException("The web books at least a day ahead");
        }
        this.reasons = reasons;
        this.booking = booking;
        this.clock = clock;
        this.daysAhead = daysAhead;
    }

    /** The reasons booked on the web, by practitioner, and the days. */
    @Transactional(readOnly = true)
    public Options options() {
        LocalDate today = LocalDate.now(clock);
        return new Options(reasons.all().stream().filter(BookableReason::webBookable).toList(),
                WebBookingPolicy.firstDay(today), WebBookingPolicy.lastDay(today, daysAhead));
    }

    /**
     * The times offered for that reason on that day.
     *
     * @throws WebBookingRefusedException when the reason is not booked on the web, or the day is out of range
     */
    @Transactional(readOnly = true)
    public List<LocalTime> times(UUID practitionerId, UUID reasonId, LocalDate date) {
        BookableReason reason = reasons.all().stream()
                .filter(candidate -> candidate.practitionerId().equals(practitionerId)
                        && candidate.reasonId().equals(reasonId) && candidate.webBookable())
                .findFirst()
                .orElseThrow(() -> new WebBookingRefusedException(Reason.NOT_ON_THE_WEB,
                        "That reason is not booked on the web"));
        if (!WebBookingPolicy.isBookableDay(date, LocalDate.now(clock), daysAhead)) {
            throw new WebBookingRefusedException(Reason.DAY_OUT_OF_RANGE,
                    "The web books from tomorrow to " + daysAhead + " days ahead");
        }
        return booking.offeredTimes(reason.practitionerId(), reason.reasonId(), date);
    }

    /** The reasons booked on the web, and the first and last day the web books. */
    public record Options(List<BookableReason> reasons, LocalDate firstDay, LocalDate lastDay) {
    }
}
