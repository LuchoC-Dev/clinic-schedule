package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Means;

/**
 * A day of the cash desk as the admins get it (D-87): each charge and the totals, overall and by who
 * recorded them. Never the patients, whom the admins do not read, nor the pending visits, which are
 * patients waiting to pay: this type has no place for either.
 */
record CashDeskLogResponse(LocalDate date, List<Movement> movements, Total total, List<RecorderTotal> byRecorder) {

    record Movement(BigDecimal amount, Means means, String recordedBy, LocalDateTime recordedAt) {
    }

    record Total(int count, BigDecimal amount) {
    }

    record RecorderTotal(String recordedBy, int count, BigDecimal amount) {
    }

    static CashDeskLogResponse of(CashDeskDay day) {
        return new CashDeskLogResponse(day.date(),
                day.charged().stream().map(visit -> movement(visit.charge().orElseThrow())).toList(),
                new Total(day.total().count(), day.total().amount()),
                day.byRecorder().stream()
                        .map(each -> new RecorderTotal(each.recordedBy(), each.total().count(), each.total().amount()))
                        .toList());
    }

    private static Movement movement(Charge charge) {
        return new Movement(charge.amount().value(), charge.means(), charge.recordedBy(), charge.recordedAt());
    }
}
