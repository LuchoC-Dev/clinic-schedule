package com.clinic.schedule.arrival.session.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.clinic.schedule.arrival.session.domain.SessionEvent.AutoCloseTurnedOff;
import com.clinic.schedule.arrival.session.domain.SessionEvent.EmergencyDeclared;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientCalled;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientDidNotComeIn;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientDidNotShowUp;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientLeft;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientSeen;
import com.clinic.schedule.arrival.session.domain.SessionEvent.SessionClosed;
import com.clinic.schedule.arrival.session.domain.SessionEvent.SessionOpened;
import com.clinic.schedule.arrival.session.domain.exception.SessionRuleViolationException;
import com.clinic.schedule.arrival.session.domain.exception.SessionRuleViolationException.Reason;

/**
 * One opening of a queue, from when reception opens it until it closes (D-63): who came in, in
 * which order, who was called by whom, and how each left the queue. A queue left open past
 * midnight is still one session; one reopened is a new one, so whoever was not seen does not come
 * back.
 *
 * <p>Like the queue's setup, it is its events: {@link #replay} adds them up, deciding answers with
 * the events that say so, and applying them gives the new session. The decisions are package-private;
 * the way in is {@link QueueSessionService}, which knows what a session alone cannot — who serves the
 * queue, whether another session is open, how many calls a patient gets.
 */
public final class QueueSession {

    private final SessionId id;
    private final UUID queueId;
    private final LocalDateTime openedAt;
    private final LocalDateTime closedAt;
    private final boolean autoClose;
    private final List<Entry> entries;
    private final int calls;
    private final int version;

    private QueueSession(SessionId id, UUID queueId, LocalDateTime openedAt, LocalDateTime closedAt,
            boolean autoClose, List<Entry> entries, int calls, int version) {
        this.id = id;
        this.queueId = queueId;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.autoClose = autoClose;
        this.entries = List.copyOf(entries);
        this.calls = calls;
        this.version = version;
    }

    /** @throws IllegalArgumentException when the events do not start with the opening, or describe what no session could be */
    public static QueueSession replay(SessionId id, List<SessionEvent> events) {
        if (id == null || events == null || events.isEmpty() || !(events.get(0) instanceof SessionOpened opened)
                || opened.queueId() == null || opened.at() == null) {
            throw new IllegalArgumentException("A session starts with its opening");
        }
        QueueSession session = new QueueSession(id, opened.queueId(), opened.at(), null, true, List.of(), 0, 1);
        for (SessionEvent event : events.subList(1, events.size())) {
            session = session.apply(event);
        }
        return session;
    }

    /** This session after the event: a new one. */
    public QueueSession apply(SessionEvent event) {
        if (!isOpen()) {
            throw new IllegalArgumentException("Nothing happens to a session once it is closed");
        }
        return switch (event) {
            case SessionOpened opened -> throw new IllegalArgumentException("A session is opened only once");
            case PatientArrived arrived -> {
                List<Entry> after = new ArrayList<>(entries);
                after.add(Entry.arrived(new EntryId(arrived.entryId()), arrived.patientId(), entries.size() + 1,
                        arrived.emergency(), arrived.at()));
                yield with(after, calls);
            }
            case EmergencyDeclared declared -> changing(declared.entryId(), entry -> entry.asEmergency(declared.at()), calls);
            case PatientCalled called ->
                    changing(called.entryId(), entry -> entry.calledBy(called.practitionerId(), called.at()), calls + 1);
            case PatientSeen seen -> changing(seen.entryId(), entry -> entry.in(EntryState.SEEN), calls);
            case PatientDidNotComeIn missed -> changing(missed.entryId(), entry -> entry.passedOverAt(calls + 1), calls);
            case PatientDidNotShowUp gone -> changing(gone.entryId(), entry -> entry.in(EntryState.DID_NOT_SHOW_UP), calls);
            case PatientLeft left -> changing(left.entryId(), entry -> entry.in(EntryState.LEFT), calls);
            case AutoCloseTurnedOff off ->
                    new QueueSession(id, queueId, openedAt, null, false, entries, calls, version + 1);
            case SessionClosed closed -> new QueueSession(id, queueId, openedAt, closed.at(), autoClose,
                    entries.stream().map(entry -> entry.isWaiting() ? entry.in(EntryState.NOT_SEEN) : entry).toList(),
                    calls, version + 1);
        };
    }

    // --- decisions: each answers with the event that records it -------------------------------

    PatientArrived arrive(UUID patientId, boolean emergency, LocalDateTime at) {
        if (patientId == null) {
            throw new IllegalArgumentException("A patient is needed to come in");
        }
        if (entries.stream().anyMatch(entry -> entry.isWaiting() && entry.patientId().equals(patientId))) {
            throw new SessionRuleViolationException(Reason.ALREADY_WAITING);
        }
        return new PatientArrived(EntryId.newId().value(), patientId, emergency, at);
    }

    EmergencyDeclared declareEmergency(EntryId entryId, LocalDateTime at) {
        Entry entry = waiting(entryId);
        if (entry.isEmergency()) {
            throw new SessionRuleViolationException(Reason.ALREADY_EMERGENCY);
        }
        return new EmergencyDeclared(entryId.value(), at);
    }

    PatientLeft leave(EntryId entryId) {
        waiting(entryId);
        return new PatientLeft(entryId.value());
    }

    /** @throws SessionRuleViolationException when the practitioner still has someone called, or nobody waits */
    PatientCalled callNext(UUID practitionerId, LocalDateTime at) {
        if (calledBy(practitionerId).isPresent()) {
            throw new SessionRuleViolationException(Reason.STILL_CALLED);
        }
        Entry next = CallOrder.next(entries, calls + 1)
                .orElseThrow(() -> new SessionRuleViolationException(Reason.NOBODY_WAITING));
        return new PatientCalled(next.id().value(), practitionerId, at);
    }

    PatientSeen finish(UUID practitionerId) {
        return new PatientSeen(called(practitionerId).id().value());
    }

    /** Passed over at the next call, or out of the queue once they had their calls. */
    SessionEvent didNotComeIn(UUID practitionerId, int callsBeforeNoShow) {
        Entry entry = called(practitionerId);
        return entry.timesCalled() >= callsBeforeNoShow
                ? new PatientDidNotShowUp(entry.id().value())
                : new PatientDidNotComeIn(entry.id().value());
    }

    AutoCloseTurnedOff turnOffAutoClose() {
        if (!autoClose) {
            throw new SessionRuleViolationException(Reason.AUTO_CLOSE_ALREADY_OFF);
        }
        return new AutoCloseTurnedOff();
    }

    SessionClosed close(LocalDateTime at) {
        return new SessionClosed(at);
    }

    /** Whether it closes by itself now: it still would, and its day is over. */
    public boolean isDueToClose(LocalDateTime now) {
        return isOpen() && autoClose && now.toLocalDate().isAfter(openedAt.toLocalDate());
    }

    /** The patient the practitioner has called and not finished with, if any. */
    public Optional<Entry> calledBy(UUID practitionerId) {
        return entries.stream()
                .filter(entry -> entry.state() == EntryState.CALLED && practitionerId.equals(entry.withPractitioner()))
                .findFirst();
    }

    private Entry called(UUID practitionerId) {
        return calledBy(practitionerId)
                .orElseThrow(() -> new SessionRuleViolationException(Reason.NOBODY_CALLED));
    }

    private Entry waiting(EntryId entryId) {
        Entry entry = find(entryId);
        if (!entry.isWaiting()) {
            throw new SessionRuleViolationException(Reason.NOT_WAITING);
        }
        return entry;
    }

    private Entry find(EntryId entryId) {
        return entries.stream().filter(entry -> entry.id().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such entry in this session: " + entryId.value()));
    }

    private QueueSession changing(UUID entryId, UnaryOperator<Entry> change, int newCalls) {
        EntryId target = new EntryId(entryId);
        find(target);
        return with(entries.stream().map(entry -> entry.id().equals(target) ? change.apply(entry) : entry).toList(),
                newCalls);
    }

    private QueueSession with(List<Entry> newEntries, int newCalls) {
        return new QueueSession(id, queueId, openedAt, closedAt, autoClose, newEntries, newCalls, version + 1);
    }

    public SessionId id() {
        return id;
    }

    public UUID queueId() {
        return queueId;
    }

    public LocalDateTime openedAt() {
        return openedAt;
    }

    /** Null while it is open. */
    public LocalDateTime closedAt() {
        return closedAt;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    /** Whether it closes by itself at the end of its day. */
    public boolean closesByItself() {
        return autoClose;
    }

    /** Everyone who came in, in the order they did. */
    public List<Entry> entries() {
        return entries;
    }

    /** How many calls were made in it: the next one is this plus one. */
    public int calls() {
        return calls;
    }

    public int version() {
        return version;
    }
}
