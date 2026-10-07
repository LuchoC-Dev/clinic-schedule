package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;
import java.util.List;

import com.clinic.schedule.cashdesk.domain.CashDeskDay;

/**
 * What a day's movements add up to, as both of the day's answers give it (D-88): charged, given back and
 * what is left, overall and by who recorded them.
 */
final class Totals {

    private Totals() {
    }

    record Sum(int count, BigDecimal amount) {
    }

    record Total(Sum charged, Sum refunded, BigDecimal left) {

        static Total of(CashDeskDay.Total total) {
            return new Total(new Sum(total.charged().count(), total.charged().amount()),
                    new Sum(total.refunded().count(), total.refunded().amount()), total.left());
        }
    }

    record RecorderTotal(String recordedBy, Sum charged, Sum refunded, BigDecimal left) {
    }

    static List<RecorderTotal> byRecorder(CashDeskDay day) {
        return day.byRecorder().stream()
                .map(each -> {
                    Total total = Total.of(each.total());
                    return new RecorderTotal(each.recordedBy(), total.charged(), total.refunded(), total.left());
                })
                .toList();
    }
}
