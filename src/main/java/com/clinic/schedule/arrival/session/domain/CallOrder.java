package com.clinic.schedule.arrival.session.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is called next (D-63, D-64): the one rule of the order, which knows nothing of screens or
 * modules.
 *
 * <ul>
 *   <li>Emergencies first, from when they were declared; then patients with an appointment, by the
 *       later of its time and their arrival; then everyone else, by arrival. Two at the same time
 *       keep the order they came in.</li>
 *   <li>A patient kept for a practitioner is called by that one only.</li>
 *   <li>Whoever did not come in when called is passed over at the next call only, unless nobody
 *       else that practitioner may call is waiting.</li>
 * </ul>
 */
public final class CallOrder {

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt(CallOrder::tier)
            .thenComparing(Entry::placedAt)
            .thenComparingInt(Entry::sequence);

    private CallOrder() {
    }

    /** The waiting entries, in the order they will be called — passing over nobody. */
    public static List<Entry> of(List<Entry> entries) {
        return entries.stream().filter(Entry::isWaiting).sorted(ORDER).toList();
    }

    /** Who the given call of the session goes to, when that practitioner calls, if anyone. */
    public static Optional<Entry> next(List<Entry> entries, int call, UUID practitionerId) {
        List<Entry> callable = of(entries).stream().filter(entry -> entry.isCallableBy(practitionerId)).toList();
        return callable.stream()
                .filter(entry -> entry.passedOverAtCall() != call)
                .findFirst()
                .or(() -> callable.stream().findFirst());
    }

    /** The waiting entries nobody may call: kept for a practitioner who no longer serves the queue. */
    public static List<Entry> uncallable(List<Entry> entries, Collection<UUID> servedBy) {
        return of(entries).stream()
                .filter(entry -> entry.reservedFor() != null && !servedBy.contains(entry.reservedFor()))
                .toList();
    }

    private static int tier(Entry entry) {
        if (entry.isEmergency()) {
            return 0;
        }
        return entry.hasAppointment() ? 1 : 2;
    }
}
