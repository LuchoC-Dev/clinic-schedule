package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class PatientTest {

    private static final IdentityDocument DNI = new IdentityDocument(DocumentType.DNI, "30111222");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 15);

    private static Patient verified() {
        return Patient.register("Ana Pérez", DNI, true);
    }

    @Test
    void aPatientWhoGivesANameIsDeclaredUntilTheirDocumentIsSeen() {
        assertThat(Patient.register("Ana Pérez", DNI, false).getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);
        assertThat(Patient.register("Ana Pérez", null, false).getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);
        assertThat(verified().getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
    }

    @Test
    void aDocumentCannotBeSeenIfThereIsNone() {
        assertThatThrownBy(() -> Patient.register("Ana Pérez", null, true))
                .isInstanceOf(UnverifiableIdentityException.class);
    }

    @Test
    void aPatientWhoGivesNoNameGoesByTheirAlias() {
        Patient patient = Patient.registerUnidentified("NN-4821");

        assertThat(patient.getFullName()).isEqualTo("NN-4821");
        assertThat(patient.getAlias()).isEqualTo("NN-4821");
        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.ALIAS);
        assertThat(patient.getDocument()).isNull();
    }

    @Test
    void anAliasIsNnAndFourDigits() {
        assertThatIllegalArgumentException().isThrownBy(() -> Patient.registerUnidentified("NN-482"));
        assertThatIllegalArgumentException().isThrownBy(() -> Patient.registerUnidentified("XX-4821"));
        assertThatIllegalArgumentException().isThrownBy(() -> Patient.registerUnidentified(null));
    }

    @Test
    void givingTheNameLaterKeepsTheAliasAndSaysWhatChanged() {
        Patient patient = Patient.registerUnidentified("NN-4821");

        PatientIdentityChange change = patient.changeIdentity("Ana Pérez", null, false, false, NOW, "recepcion")
                .orElseThrow();

        assertThat(patient.getFullName()).isEqualTo("Ana Pérez");
        assertThat(patient.getAlias()).isEqualTo("NN-4821");
        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);
        assertThat(change.getPatientId()).isEqualTo(patient.getId());
        assertThat(change.getChangedAt()).isEqualTo(NOW);
        assertThat(change.getChangedBy()).isEqualTo("recepcion");
        assertThat(change.getFullNameBefore()).isEqualTo("NN-4821");
        assertThat(change.getStatusBefore()).isEqualTo(IdentityStatus.ALIAS);
        assertThat(change.getFullNameAfter()).isEqualTo("Ana Pérez");
        assertThat(change.getStatusAfter()).isEqualTo(IdentityStatus.DECLARED);
    }

    @Test
    void theNameAndTheDocumentCanBeGivenAndSeenAtOnce() {
        Patient patient = Patient.registerUnidentified("NN-4821");

        PatientIdentityChange change = patient.changeIdentity("Ana Pérez", DNI, true, false, NOW, "recepcion")
                .orElseThrow();

        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
        assertThat(change.getDocumentBefore()).isNull();
        assertThat(change.getDocumentAfter()).isEqualTo(DNI);
    }

    @Test
    void aPatientStillGoingByTheirAliasCannotBeVerified() {
        Patient patient = Patient.registerUnidentified("NN-4821");

        assertThatThrownBy(() -> patient.changeIdentity("NN-4821", DNI, true, false, NOW, "recepcion"))
                .isInstanceOf(UnverifiableIdentityException.class);
        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.ALIAS);
        assertThat(patient.getDocument()).isNull();
    }

    @Test
    void aDocumentGivenWithoutANameLeavesThePatientGoingByTheirAlias() {
        Patient patient = Patient.registerUnidentified("NN-4821");

        patient.changeIdentity("NN-4821", DNI, false, false, NOW, "recepcion").orElseThrow();

        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.ALIAS);
        assertThat(patient.getDocument()).isEqualTo(DNI);
    }

    @Test
    void seeingADocumentThatIsNotThereIsRefusedAndChangesNothing() {
        Patient patient = Patient.register("Ana Pérez", null, false);

        assertThatThrownBy(() -> patient.changeIdentity("Ana María Pérez", null, true, false, NOW, "recepcion"))
                .isInstanceOf(UnverifiableIdentityException.class);
        assertThat(patient.getFullName()).isEqualTo("Ana Pérez");
    }

    @Test
    void seeingTheDocumentVerifiesAnIdentityThatOtherwiseStaysTheSame() {
        Patient patient = Patient.register("Ana Pérez", DNI, false);

        PatientIdentityChange change = patient.changeIdentity("Ana Pérez", DNI, true, false, NOW, "recepcion")
                .orElseThrow();

        assertThat(change.getStatusBefore()).isEqualTo(IdentityStatus.DECLARED);
        assertThat(change.getStatusAfter()).isEqualTo(IdentityStatus.VERIFIED);
    }

    @Test
    void aVerifiedIdentityIsDeclaredAgainOnceItsNameOrDocumentChanges() {
        Patient renamed = verified();
        renamed.changeIdentity("Ana María Pérez", DNI, false, false, NOW, "recepcion");
        assertThat(renamed.getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);

        Patient newDocument = verified();
        newDocument.changeIdentity("Ana Pérez", new IdentityDocument(DocumentType.DNI, "30111223"), false, false, NOW,
                "recepcion");
        assertThat(newDocument.getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);

        Patient seenAgain = verified();
        seenAgain.changeIdentity("Ana María Pérez", DNI, true, false, NOW, "recepcion");
        assertThat(seenAgain.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
    }

    @Test
    void savingTheSameIdentityChangesNothingAndLeavesNoTrace() {
        Patient patient = verified();

        assertThat(patient.changeIdentity("Ana Pérez", DNI, false, false, NOW, "recepcion")).isEmpty();
        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
        assertThat(Patient.register("Ana Pérez", null, false).changeIdentity(" Ana Pérez ", null, false, false, NOW,
                "recepcion")).isEmpty();
    }

    /** Their web account logs in with their document, so nothing leaves them unverified (D-79). */
    @Test
    void aPatientWithAWebAccountChangesTheirNameOrDocumentOnlyWithTheDocumentSeen() {
        Patient renamed = verified();
        assertThatThrownBy(() -> renamed.changeIdentity("Ana María Pérez", DNI, false, true, NOW, "recepcion"))
                .isInstanceOf(VerificationRequiredException.class);
        assertThat(renamed.getFullName()).isEqualTo("Ana Pérez");
        assertThat(renamed.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);

        Patient withoutDocument = verified();
        assertThatThrownBy(() -> withoutDocument.changeIdentity("Ana Pérez", null, false, true, NOW, "recepcion"))
                .isInstanceOf(VerificationRequiredException.class);

        Patient seenAgain = verified();
        assertThat(seenAgain.changeIdentity("Ana María Pérez", DNI, true, true, NOW, "recepcion")).isPresent();
        assertThat(seenAgain.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);

        assertThat(verified().changeIdentity("Ana Pérez", DNI, false, true, NOW, "recepcion")).isEmpty();
    }

    /** Registered at reception, as every patient was before the web (D-91). */
    @Test
    void aPatientRegisteredAtReceptionSaysSo() {
        assertThat(verified().getRegisteredVia()).isEqualTo(RegistrationChannel.RECEPTION);
        assertThat(Patient.registerUnidentified("NN-4821").getRegisteredVia()).isEqualTo(RegistrationChannel.RECEPTION);
    }

    /** An adult who registers on the web gives their document, which nobody has seen yet (D-91). */
    @Test
    void aPatientWhoRegistersOnTheWebIsDeclaredWithTheirDocumentAndSaysSo() {
        PatientDetails details = new PatientDetails(LocalDate.of(1990, 5, 14), Phone.ofNullable("1155550000", null),
                null, null);
        Patient patient = Patient.registerFromWeb("Ana Pérez", DNI, details, NOW.toLocalDate());

        assertThat(patient.getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);
        assertThat(patient.getRegisteredVia()).isEqualTo(RegistrationChannel.WEB);
        assertThat(patient.getDocument()).isEqualTo(DNI);
        assertThat(patient.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(patient.getPhone().number()).isEqualTo("1155550000");
        assertThat(patient.getPhone().owner()).isNull();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Patient.registerFromWeb("Ana Pérez", null, details, NOW.toLocalDate()));
        assertThatThrownBy(() -> Patient.registerFromWeb("Ana Pérez", DNI,
                new PatientDetails(LocalDate.of(2026, 3, 11), null, null, null), NOW.toLocalDate()))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    /**
     * Their account logs in with a document nobody has seen yet: saved as it is they stay declared, and a new
     * name or document goes in only with the document seen, which verifies them (D-91).
     */
    @Test
    void aPatientWithAWebAccountWhoRegisteredOnTheWebIsVerifiedOnlyWithTheirDocumentSeen() {
        PatientDetails details = new PatientDetails(LocalDate.of(1990, 5, 14), null, null, null);
        Patient unchanged = Patient.registerFromWeb("Ana Pérez", DNI, details, NOW.toLocalDate());
        assertThat(unchanged.changeIdentity("Ana Pérez", DNI, false, true, NOW, "recepcion")).isEmpty();
        assertThat(unchanged.getIdentityStatus()).isEqualTo(IdentityStatus.DECLARED);

        Patient renamed = Patient.registerFromWeb("Ana Pérez", DNI, details, NOW.toLocalDate());
        assertThatThrownBy(() -> renamed.changeIdentity("Ana María Pérez", DNI, false, true, NOW, "recepcion"))
                .isInstanceOf(VerificationRequiredException.class);
        assertThat(renamed.getFullName()).isEqualTo("Ana Pérez");

        Patient seen = Patient.registerFromWeb("Ana Pérez", DNI, details, NOW.toLocalDate());
        assertThat(seen.changeIdentity("Ana Pérez", DNI, true, true, NOW, "recepcion")).isPresent();
        assertThat(seen.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
        assertThat(seen.getRegisteredVia()).isEqualTo(RegistrationChannel.WEB);
    }
}
