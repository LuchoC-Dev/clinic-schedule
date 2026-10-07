package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Kind;
import com.clinic.schedule.cashdesk.domain.Means;

/**
 * A day of the cash desk as the admins get it (D-87, D-88): each charge and refund and the totals, overall
 * and by who recorded them. Never the patients, whom the admins do not read, nor why a refund was given, which
 * may say something of the patient, nor the pending visits, which are patients waiting to pay: this type has
 * no place for any of them.
 */
record CashDeskLogResponse(LocalDate date, List<Movement> movements, Totals.Total total,
        List<Totals.RecorderTotal> byRecorder) {

    record Movement(Kind kind, BigDecimal amount, Means means, String recordedBy, LocalDateTime recordedAt) {
    }

    static CashDeskLogResponse of(CashDeskDay day) {
        return new CashDeskLogResponse(day.date(),
                day.movements().stream()
                        .map(movement -> new Movement(movement.kind(), movement.amount().value(), movement.means(),
                                movement.recordedBy(), movement.recordedAt()))
                        .toList(),
                Totals.Total.of(day.total()), Totals.byRecorder(day));
    }
}
