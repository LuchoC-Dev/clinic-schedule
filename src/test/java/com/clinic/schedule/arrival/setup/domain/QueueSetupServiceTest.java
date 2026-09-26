package com.clinic.schedule.arrival.setup.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerJoined;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerLeft;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueRenamed;
import com.clinic.schedule.arrival.setup.domain.exception.PractitionerInAnotherQueueException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueNameTakenException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueRuleViolationException;

class QueueSetupServiceTest {

    private static final QueueSetupService SETUP = new QueueSetupService();
    private static final UUID ARIAS = UUID.randomUUID();
    private static final UUID BENITEZ = UUID.randomUUID();
    private static final QueueId ID = QueueId.newId();

    private static Queue guardia() {
        return Queue.replay(ID, List.of(SETUP.create("Guardia", null, List.of())));
    }

    @Test
    void aQueueIsWhatItsEventsAddUpTo() {
        Queue queue = Queue.replay(ID, List.of(
                new QueueCreated("Consultorio 3", "3"),
                new PractitionerJoined(ARIAS),
                new PractitionerJoined(BENITEZ),
                new QueueRenamed("Guardia", null),
                new PractitionerLeft(ARIAS)));

        assertThat(queue.name()).isEqualTo("Guardia");
        assertThat(queue.room()).isNull();
        assertThat(queue.practitioners()).containsExactly(BENITEZ);
        assertThat(queue.version()).isEqualTo(5);
    }

    @Test
    void aQueueStartsWithItsCreationAndIsCreatedOnce() {
        assertThatIllegalArgumentException().isThrownBy(() -> Queue.replay(ID, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> Queue.replay(ID, List.of(new PractitionerJoined(ARIAS))));
        assertThatIllegalArgumentException().isThrownBy(() -> Queue.replay(ID,
                List.of(new QueueCreated("Guardia", null), new QueueCreated("Otra", null))));
    }

    @Test
    void eventsThatNoQueueCouldHaveAreRefusedOnReplay() {
        assertThatIllegalArgumentException().isThrownBy(() -> Queue.replay(ID, List.of(
                new QueueCreated("Guardia", null), new PractitionerJoined(ARIAS), new PractitionerJoined(ARIAS))));
        assertThatIllegalArgumentException().isThrownBy(() -> Queue.replay(ID, List.of(
                new QueueCreated("Guardia", null), new PractitionerLeft(ARIAS))));
        assertThatIllegalArgumentException().isThrownBy(() -> Queue.replay(ID, List.of(new QueueCreated(" ", null))));
    }

    @Test
    void aNameIsRequiredAndARoomIsOptional() {
        QueueCreated created = SETUP.create("  Consultorio 3 ", "  ", List.of());

        assertThat(created.name()).isEqualTo("Consultorio 3");
        assertThat(created.room()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> SETUP.create(null, null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> SETUP.create("x".repeat(61), null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> SETUP.create("Guardia", "x".repeat(21), List.of()));
    }

    @Test
    void noTwoQueuesShareANameWhateverTheCase() {
        assertThatThrownBy(() -> SETUP.create("guardia", null, List.of("Guardia")))
                .isInstanceOf(QueueNameTakenException.class);
        assertThatThrownBy(() -> SETUP.rename(guardia(), "CONSULTORIO 3", null, List.of("Consultorio 3")))
                .isInstanceOf(QueueNameTakenException.class);
    }

    @Test
    void aQueueMayChangeItsOwnNameButNotToTheSameOne() {
        assertThat(SETUP.rename(guardia(), "Guardia", "1", List.of()).room()).isEqualTo("1");
        assertThat(SETUP.rename(guardia(), "GUARDIA", null, List.of()).name()).isEqualTo("GUARDIA");
        assertThatThrownBy(() -> SETUP.rename(guardia(), " Guardia ", "", List.of()))
                .isInstanceOf(QueueRuleViolationException.class);
    }

    @Test
    void aPractitionerServesOneQueueAtMost() {
        assertThat(SETUP.join(guardia(), ARIAS, Optional.empty())).isEqualTo(new PractitionerJoined(ARIAS));
        assertThatThrownBy(() -> SETUP.join(guardia(), ARIAS, Optional.of(QueueId.newId())))
                .isInstanceOf(PractitionerInAnotherQueueException.class);
    }

    @Test
    void aPractitionerJoinsAQueueOnceAndLeavesOnlyOneTheyServe() {
        Queue served = guardia().apply(SETUP.join(guardia(), ARIAS, Optional.empty()));

        assertThatThrownBy(() -> SETUP.join(served, ARIAS, Optional.of(ID)))
                .isInstanceOf(QueueRuleViolationException.class);
        assertThat(SETUP.leave(served, ARIAS)).isEqualTo(new PractitionerLeft(ARIAS));
        assertThatThrownBy(() -> SETUP.leave(served, BENITEZ)).isInstanceOf(QueueRuleViolationException.class);
    }

    @Test
    void decidingLeavesTheQueueAsItWasAndTheLastPractitionerMayLeave() {
        Queue queue = guardia().apply(new PractitionerJoined(ARIAS));

        PractitionerLeft left = SETUP.leave(queue, ARIAS);

        assertThat(queue.practitioners()).containsExactly(ARIAS);
        assertThat(queue.apply(left).practitioners()).isEmpty();
    }
}
