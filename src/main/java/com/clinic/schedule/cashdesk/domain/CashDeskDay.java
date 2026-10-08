package com.clinic.schedule.cashdesk.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A day of the cash desk (D-87, D-88): the charges and the refunds recorded that day, in the order they were
 * recorded, what they add up to — charged, given back, and what is left —, overall and by who recorded each,
 * each split by means (D-89), and the emergencies that came that day and were never charged. A refund counts the
 * day it is recorded, whatever day its charge was, and goes out by its charge's means. Only what is left in
 * cash is what should be in the drawer.
 *
 * @param visits the visits with a charge or a refund recorded that day, whole
 * @param pending the emergencies that came that day, never charged
 */
public record CashDeskDay(LocalDate date, List<Visit> visits, List<Visit> pending) {

    public CashDeskDay {
        if (date == null || visits == null || pending == null) {
            throw new IllegalArgumentException("A day of the cash desk needs its date, its visits and its pending ones");
        }
        if (pending.stream().anyMatch(visit -> !visit.pending())) {
            throw new IllegalArgumentException("A pending visit was never charged");
        }
        visits = List.copyOf(visits);
        pending = pending.stream().sorted(Comparator.comparing(Visit::arrivedAt)).toList();
    }

    /** What happened at the desk that day: each charge and refund recorded on it, in order. */
    public List<Movement> movements() {
        List<Movement> movements = new ArrayList<>();
        for (Visit visit : visits) {
            for (Charge charge : visit.charges()) {
                if (isToday(charge.recordedAt())) {
                    movements.add(Movement.of(visit, charge));
                }
                for (Refund refund : charge.refunds()) {
                    if (isToday(refund.recordedAt())) {
                        movements.add(Movement.of(visit, charge, refund));
                    }
                }
            }
        }
        movements.sort(Comparator.comparing(Movement::recordedAt));
        return movements;
    }

    /** Everything charged and given back that day. */
    public Total total() {
        return Total.of(movements());
    }

    /** What each user recorded, by username. */
    public List<RecorderTotal> byRecorder() {
        Map<String, List<Movement>> byUser = new TreeMap<>();
        for (Movement movement : movements()) {
            byUser.computeIfAbsent(movement.recordedBy(), user -> new ArrayList<>()).add(movement);
        }
        return byUser.entrySet().stream()
                .map(entry -> new RecorderTotal(entry.getKey(), Total.of(entry.getValue())))
                .toList();
    }

    private boolean isToday(LocalDateTime at) {
        return at.toLocalDate().equals(date);
    }

    /** A charge or a refund. */
    public enum Kind {
        CHARGE, REFUND
    }

    /**
     * Money that came in or went out, with its visit and its charge.
     *
     * @param reason why it was given back; null for a charge
     */
    public record Movement(Kind kind, Visit visit, Charge charge, Amount amount, String reason, String recordedBy,
            LocalDateTime recordedAt) {

        static Movement of(Visit visit, Charge charge) {
            return new Movement(Kind.CHARGE, visit, charge, charge.amount(), null, charge.recordedBy(),
                    charge.recordedAt());
        }

        static Movement of(Visit visit, Charge charge, Refund refund) {
            return new Movement(Kind.REFUND, visit, charge, refund.amount(), refund.reason(), refund.recordedBy(),
                    refund.recordedAt());
        }

        public UUID visitId() {
            return visit.id();
        }

        public Means means() {
            return charge.means();
        }
    }

    /**
     * Some charges and refunds: how many of each, what each adds up to, and what is left, overall and by means.
     *
     * @param byMeans the same, for each means with a movement, in the order the means are declared
     */
    public record Total(Sum charged, Sum refunded, List<MeansTotal> byMeans) {

        public Total {
            byMeans = List.copyOf(byMeans);
        }

        static Total of(List<Movement> movements) {
            Map<Means, List<Movement>> split = new EnumMap<>(Means.class);
            for (Movement movement : movements) {
                split.computeIfAbsent(movement.means(), means -> new ArrayList<>()).add(movement);
            }
            return new Total(Sum.of(movements, Kind.CHARGE), Sum.of(movements, Kind.REFUND),
                    split.entrySet().stream()
                            .map(entry -> new MeansTotal(entry.getKey(), Sum.of(entry.getValue(), Kind.CHARGE),
                                    Sum.of(entry.getValue(), Kind.REFUND)))
                            .toList());
        }

        /** What was charged, less what was given back. */
        public BigDecimal left() {
            return charged.amount().subtract(refunded.amount());
        }

        /** What should be in the drawer: what is left in cash, the only means that goes into it. */
        public BigDecimal drawer() {
            return byMeans.stream()
                    .filter(each -> each.means() == Means.CASH)
                    .map(MeansTotal::left)
                    .findFirst()
                    .orElse(BigDecimal.ZERO.setScale(2));
        }
    }

    /** What was charged and given back by one means. */
    public record MeansTotal(Means means, Sum charged, Sum refunded) {

        /** What was charged by it, less what was given back by it. */
        public BigDecimal left() {
            return charged.amount().subtract(refunded.amount());
        }
    }

    /** How many movements of a kind, and what they add up to. */
    public record Sum(int count, BigDecimal amount) {

        static Sum of(List<Movement> movements, Kind kind) {
            List<Movement> ofKind = movements.stream().filter(movement -> movement.kind() == kind).toList();
            return new Sum(ofKind.size(), ofKind.stream()
                    .map(movement -> movement.amount().value())
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        }
    }

    /** What one user recorded. */
    public record RecorderTotal(String recordedBy, Total total) {
    }
}
