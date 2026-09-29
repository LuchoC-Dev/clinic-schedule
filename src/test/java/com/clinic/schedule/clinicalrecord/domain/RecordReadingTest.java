package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RecordReadingTest {

    private static final PractitionerId PRACTITIONER = new PractitionerId(UUID.randomUUID());
    private static final String USER = "arias";
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 3, 10, 10, 15);

    @Test
    void aReadingOfTheWholeRecordNamesNoFile() {
        RecordReading reading = RecordReading.ofRecord(PRACTITIONER, USER, PATIENT, NOW, ReadAccess.VISIT_TODAY);

        assertThat(reading.practitionerId()).isEqualTo(PRACTITIONER);
        assertThat(reading.readBy()).contains(USER);
        assertThat(reading.patientId()).isEqualTo(PATIENT);
        assertThat(reading.readAt()).isEqualTo(NOW);
        assertThat(reading.access()).isEqualTo(ReadAccess.VISIT_TODAY);
        assertThat(reading.attachmentId()).isEmpty();
    }

    @Test
    void aReadingOfAnAttachmentNamesIt() {
        AttachmentId attachment = AttachmentId.newId();

        assertThat(RecordReading.ofAttachment(PRACTITIONER, USER, PATIENT, NOW, ReadAccess.ACCESS_GRANT, attachment)
                .attachmentId()).contains(attachment);
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofAttachment(PRACTITIONER, USER, PATIENT, NOW, ReadAccess.ACCESS_GRANT, null));
    }

    @Test
    void aReadingNeedsWhoWhoseWhenAndHow() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofRecord(null, USER, PATIENT, NOW, ReadAccess.VISIT_TODAY));
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofRecord(PRACTITIONER, USER, null, NOW, ReadAccess.VISIT_TODAY));
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofRecord(PRACTITIONER, USER, PATIENT, null, ReadAccess.VISIT_TODAY));
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofRecord(PRACTITIONER, USER, PATIENT, NOW, null));
    }

    /** Every reading from now on names the user; only those restored from before may lack one (D-71). */
    @Test
    void aNewReadingNeedsTheUserWhoReadIt() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofRecord(PRACTITIONER, null, PATIENT, NOW, ReadAccess.VISIT_TODAY));
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordReading.ofAttachment(PRACTITIONER, " ", PATIENT, NOW, ReadAccess.VISIT_TODAY,
                        AttachmentId.newId()));

        assertThat(RecordReading.restore(RecordReadingId.newId(), PRACTITIONER, null, PATIENT, NOW,
                ReadAccess.VISIT_TODAY, null).readBy()).isEmpty();
    }
}
