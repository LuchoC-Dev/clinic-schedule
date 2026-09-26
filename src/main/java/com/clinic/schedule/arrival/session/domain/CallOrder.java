package com.clinic.schedule.arrival.session.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Who is called next (D-63): the one rule of the order, which knows nothing of screens or modules.
 *
 * <ul>
 *   <li>Emergencies first, in the order they were declared; then everyone else, in the order they
 *       arrived. Two at the same time are told apart by the order they came in.</li>
 *   <li>Whoever did not come in when called is passed over at the next call only, unless nobody
 *       else is waiting.</li>
 * </ul>
 *
 * <p>The patients with an appointment, placed by the time, join the order in part 5b.
 */
public final class CallOrder {

    private static final Comparator<Entry> ORDER = Comparator
            .comparing((Entry entry) -> !entry.isEmergency())
            .thenComparing(entry -> entry.isEmergency() ? entry.emergencySince() : entry.arrivedAt())
            .thenComparingInt(Entry::sequence);

    private CallOrder() {
    }

    /** The waiting entries, in the order they will be called — passing over nobody. */
    public static List<Entry> of(List<Entry> entries) {
        return entries.stream().filter(Entry::isWaiting).sorted(ORDER).toList();
    }

    /** Who the given call of the session goes to, if anyone is waiting. */
    public static Optional<Entry> next(List<Entry> entries, int call) {
        List<Entry> waiting = of(entries);
        return waiting.stream()
                .filter(entry -> entry.passedOverAtCall() != call)
                .findFirst()
                .or(() -> waiting.stream().findFirst());
    }
}
