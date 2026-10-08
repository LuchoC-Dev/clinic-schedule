package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;
import java.util.List;

import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.Means;

/**
 * What a day's movements add up to, as both of the day's answers give it (D-88): charged, given back and
 * what is left, overall and by who recorded them, each split by means, with what should be in the drawer —
 * what is left in cash (D-89).
 */
final class Totals {

    private Totals() {
    }

    record Sum(int count, BigDecimal amount) {

        static Sum of(CashDeskDay.Sum sum) {
            return new Sum(sum.count(), sum.amount());
        }
    }

    record MeansTotal(Means means, Sum charged, Sum refunded, BigDecimal left) {
    }

    record Total(Sum charged, Sum refunded, BigDecimal left, List<MeansTotal> byMeans, BigDecimal drawer) {

        static Total of(CashDeskDay.Total total) {
            return new Total(Sum.of(total.charged()), Sum.of(total.refunded()), total.left(),
                    total.byMeans().stream()
                            .map(each -> new MeansTotal(each.means(), Sum.of(each.charged()), Sum.of(each.refunded()),
                                    each.left()))
                            .toList(),
                    total.drawer());
        }
    }

    record RecorderTotal(String recordedBy, Sum charged, Sum refunded, BigDecimal left, List<MeansTotal> byMeans,
            BigDecimal drawer) {
    }

    static List<RecorderTotal> byRecorder(CashDeskDay day) {
        return day.byRecorder().stream()
                .map(each -> {
                    Total total = Total.of(each.total());
                    return new RecorderTotal(each.recordedBy(), total.charged(), total.refunded(), total.left(),
                            total.byMeans(), total.drawer());
                })
                .toList();
    }
}
