package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TimeSlotTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    @Test
    void endsAfterItsDuration() {
        assertThat(TimeSlot.of(TEN, 30).endsAt()).isEqualTo(LocalDateTime.of(2026, 3, 10, 10, 30));
    }

    @Test
    void backToBackSlotsDoNotOverlap() {
        TimeSlot tenToTenThirty = TimeSlot.of(TEN, 30);
        TimeSlot tenThirtyToEleven = TimeSlot.of(TEN.plusMinutes(30), 30);

        assertThat(tenToTenThirty.overlaps(tenThirtyToEleven)).isFalse();
        assertThat(tenThirtyToEleven.overlaps(tenToTenThirty)).isFalse();
    }

    @Test
    void oneMinuteOfSharedTimeIsAnOverlap() {
        TimeSlot tenToTenThirty = TimeSlot.of(TEN, 30);
        TimeSlot tenTwentyNine = TimeSlot.of(TEN.plusMinutes(29), 30);

        assertThat(tenToTenThirty.overlaps(tenTwentyNine)).isTrue();
        assertThat(tenTwentyNine.overlaps(tenToTenThirty)).isTrue();
    }

    @Test
    void aSlotContainedInAnotherOneOverlaps() {
        TimeSlot longOne = TimeSlot.of(TEN, 60);
        TimeSlot shortOne = TimeSlot.of(TEN.plusMinutes(15), 15);

        assertThat(longOne.overlaps(shortOne)).isTrue();
        assertThat(shortOne.overlaps(longOne)).isTrue();
    }

    @Test
    void aSlotOverlapsItself() {
        TimeSlot slot = TimeSlot.of(TEN, 30);

        assertThat(slot.overlaps(slot)).isTrue();
    }

    @Test
    void slotsApartDoNotOverlap() {
        assertThat(TimeSlot.of(TEN, 30).overlaps(TimeSlot.of(TEN.plusHours(2), 30))).isFalse();
    }

    @Test
    void aSlotNeedsAPositiveDuration() {
        assertThatIllegalArgumentException().isThrownBy(() -> TimeSlot.of(TEN, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> TimeSlot.of(TEN, -15));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeSlot(TEN, null));
    }

    @Test
    void aSlotNeedsAStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeSlot(null, Duration.ofMinutes(30)));
    }
}
