package com.clinic.schedule.arrival.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientDidNotComeIn;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientDidNotShowUp;
import com.clinic.schedule.arrival.session.domain.exception.NotServingThisQueueException;
import com.clinic.schedule.arrival.session.domain.exception.QueueAlreadyOpenException;
import com.clinic.schedule.arrival.session.domain.exception.SessionRuleViolationException;

class QueueSessionTest {

    private static final QueueSessionService SESSIONS = new QueueSessionService(3);
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final UUID QUEUE = UUID.randomUUID();
    private static final UUID ARIAS = UUID.randomUUID();
    private static final UUID BENITEZ = UUID.randomUUID();
    private static final List<UUID> SERVED_BY = List.of(ARIAS, BENITEZ);

    private final List<SessionEvent> events = new ArrayList<>();
    private QueueSession session;

    @BeforeEach
    void open() {
        record(SESSIONS.open(QUEUE, Optional.empty(), NINE));
    }

    /** Records the event, as a command would, and keeps the session it leads to. */
    private void record(SessionEvent event) {
        events.add(event);
        session = QueueSession.replay(new SessionId(UUID.nameUUIDFromBytes(QUEUE.toString().getBytes())), events);
    }

    private UUID arrives(String name, boolean emergency, int minutesPastNine) {
        PatientArrived arrived = SESSIONS.arrive(session, UUID.nameUUIDFromBytes(name.getBytes()), emergency,
                NINE.plusMinutes(minutesPastNine));
        record(arrived);
        return arrived.patientId();
    }

    private UUID called(UUID practitioner) {
        record(SESSIONS.callNext(session, practitioner, SERVED_BY, NINE.plusHours(1)));
        return session.calledBy(practitioner).orElseThrow().patientId();
    }

    @Test
    void emergenciesFirstThenEveryoneByArrival() {
        UUID ana = arrives("Ana", false, 0);
        UUID beto = arrives("Beto", false, 5);
        UUID carla = arrives("Carla", true, 10);

        assertThat(CallOrder.of(session.entries())).extracting(Entry::patientId).containsExactly(carla, ana, beto);
    }

    @Test
    void anEmergencyDeclaredWhileWaitingCountsFromThen() {
        UUID ana = arrives("Ana", false, 0);
        UUID beto = arrives("Beto", true, 20);
        UUID carla = arrives("Carla", false, 5);
        record(SESSIONS.declareEmergency(session, entryOf(carla), NINE.plusMinutes(30)));

        assertThat(CallOrder.of(session.entries())).extracting(Entry::patientId).containsExactly(beto, carla, ana);
    }

    @Test
    void twoArrivalsAtTheSameTimeKeepTheOrderTheyCameIn() {
        UUID ana = arrives("Ana", false, 0);
        UUID beto = arrives("Beto", false, 0);

        assertThat(CallOrder.of(session.entries())).extracting(Entry::patientId).containsExactly(ana, beto);
    }

    @Test
    void thePractitionerCallsTheFirstAndHasOneCalledAtATime() {
        UUID ana = arrives("Ana", false, 0);
        arrives("Beto", false, 5);

        assertThat(called(ARIAS)).isEqualTo(ana);
        assertThatThrownBy(() -> SESSIONS.callNext(session, ARIAS, SERVED_BY, NINE.plusHours(1)))
                .isInstanceOf(SessionRuleViolationException.class);

        record(SESSIONS.finish(session, ARIAS));
        assertThat(entry(ana).state()).isEqualTo(EntryState.SEEN);
        assertThat(entry(ana).withPractitioner()).isEqualTo(ARIAS);
    }

    @Test
    void twoPractitionersOfASharedQueueEachCallTheNext() {
        UUID ana = arrives("Ana", false, 0);
        UUID beto = arrives("Beto", false, 5);

        assertThat(called(ARIAS)).isEqualTo(ana);
        assertThat(called(BENITEZ)).isEqualTo(beto);
    }

    @Test
    void onlyWhoServesTheQueueCalls() {
        arrives("Ana", false, 0);

        assertThatThrownBy(() -> SESSIONS.callNext(session, UUID.randomUUID(), SERVED_BY, NINE))
                .isInstanceOf(NotServingThisQueueException.class);
    }

    @Test
    void nobodyToCallIsARefusal() {
        assertThatThrownBy(() -> SESSIONS.callNext(session, ARIAS, SERVED_BY, NINE))
                .isInstanceOf(SessionRuleViolationException.class);
    }

    @Test
    void whoDidNotComeInIsPassedOverAtTheNextCallOnly() {
        UUID ana = arrives("Ana", false, 0);
        UUID beto = arrives("Beto", false, 5);
        UUID carla = arrives("Carla", false, 10);
        called(ARIAS);

        record(SESSIONS.didNotComeIn(session, ARIAS));

        assertThat(entry(ana).state()).isEqualTo(EntryState.WAITING);
        assertThat(entry(ana).withPractitioner()).isNull();
        assertThat(called(ARIAS)).isEqualTo(beto);
        record(SESSIONS.finish(session, ARIAS));
        assertThat(called(ARIAS)).isEqualTo(ana);
        record(SESSIONS.finish(session, ARIAS));
        assertThat(called(ARIAS)).isEqualTo(carla);
    }

    @Test
    void whoDidNotComeInIsCalledAgainWhenNobodyElseWaits() {
        UUID ana = arrives("Ana", false, 0);
        called(ARIAS);
        record(SESSIONS.didNotComeIn(session, ARIAS));

        assertThat(called(ARIAS)).isEqualTo(ana);
    }

    @Test
    void afterTheirThirdCallWithoutComingInTheyAreOutOfTheQueue() {
        UUID ana = arrives("Ana", false, 0);
        for (int call = 1; call <= 2; call++) {
            called(ARIAS);
            SessionEvent missed = SESSIONS.didNotComeIn(session, ARIAS);
            assertThat(missed).isInstanceOf(PatientDidNotComeIn.class);
            record(missed);
        }
        called(ARIAS);

        SessionEvent third = SESSIONS.didNotComeIn(session, ARIAS);

        assertThat(third).isInstanceOf(PatientDidNotShowUp.class);
        record(third);
        assertThat(entry(ana).state()).isEqualTo(EntryState.DID_NOT_SHOW_UP);
        assertThat(CallOrder.of(session.entries())).isEmpty();
    }

    @Test
    void theCallsBeforeANoShowAreASetting() {
        QueueSessionService once = new QueueSessionService(1);
        arrives("Ana", false, 0);
        called(ARIAS);

        assertThat(once.didNotComeIn(session, ARIAS)).isInstanceOf(PatientDidNotShowUp.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new QueueSessionService(0));
    }

    @Test
    void finishingOrMissingNeedsSomeoneCalled() {
        assertThatThrownBy(() -> SESSIONS.finish(session, ARIAS)).isInstanceOf(SessionRuleViolationException.class);
        assertThatThrownBy(() -> SESSIONS.didNotComeIn(session, ARIAS)).isInstanceOf(SessionRuleViolationException.class);
    }

    @Test
    void aPatientWaitsInAQueueOnceButMayComeBackAfterBeingSeen() {
        arrives("Ana", false, 0);

        assertThatThrownBy(() -> arrives("Ana", false, 1)).isInstanceOf(SessionRuleViolationException.class);
        called(ARIAS);
        record(SESSIONS.finish(session, ARIAS));
        arrives("Ana", false, 90);
        assertThat(CallOrder.of(session.entries())).hasSize(1);
    }

    @Test
    void onlyAWaitingPatientLeavesOrBecomesAnEmergency() {
        UUID ana = arrives("Ana", true, 0);

        assertThatThrownBy(() -> SESSIONS.declareEmergency(session, entryOf(ana), NINE))
                .isInstanceOf(SessionRuleViolationException.class);
        record(SESSIONS.leave(session, entryOf(ana)));
        assertThat(entry(ana).state()).isEqualTo(EntryState.LEFT);
        assertThatThrownBy(() -> SESSIONS.leave(session, entryOf(ana))).isInstanceOf(SessionRuleViolationException.class);
    }

    @Test
    void closingLeavesWhoWaitedNotSeenAndWhoWasCalledAsTheyWere() {
        UUID ana = arrives("Ana", false, 0);
        UUID beto = arrives("Beto", false, 5);
        called(ARIAS);

        record(SESSIONS.close(session, NINE.plusHours(8)));

        assertThat(session.isOpen()).isFalse();
        assertThat(entry(ana).state()).isEqualTo(EntryState.CALLED);
        assertThat(entry(beto).state()).isEqualTo(EntryState.NOT_SEEN);
        assertThatIllegalArgumentException().isThrownBy(() -> session.apply(new SessionEvent.PatientLeft(entryOf(beto).value())));
    }

    @Test
    void aQueueHasOneOpenSessionAtATime() {
        assertThatThrownBy(() -> SESSIONS.open(QUEUE, Optional.of(SessionId.newId()), NINE))
                .isInstanceOf(QueueAlreadyOpenException.class);
    }

    @Test
    void aSessionClosesByItselfOnceItsDayIsOverUnlessReceptionSaidNo() {
        LocalDateTime midnight = NINE.toLocalDate().plusDays(1).atStartOfDay();

        assertThat(SESSIONS.closeIfDue(session, midnight.minusMinutes(1))).isEmpty();
        assertThat(SESSIONS.closeIfDue(session, midnight)).hasSize(1);

        record(SESSIONS.turnOffAutoClose(session));
        assertThat(SESSIONS.closeIfDue(session, midnight.plusHours(3))).isEmpty();
        assertThatThrownBy(() -> SESSIONS.turnOffAutoClose(session)).isInstanceOf(SessionRuleViolationException.class);
    }

    @Test
    void aSessionStartsWithItsOpening() {
        assertThatIllegalArgumentException().isThrownBy(() -> QueueSession.replay(SessionId.newId(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> QueueSession.replay(SessionId.newId(),
                List.of(new SessionEvent.AutoCloseTurnedOff())));
    }

    private Entry entry(UUID patient) {
        return session.entries().stream().filter(entry -> entry.patientId().equals(patient)).reduce((a, b) -> b)
                .orElseThrow();
    }

    private EntryId entryOf(UUID patient) {
        return entry(patient).id();
    }
}
