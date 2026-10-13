package com.clinic.schedule.clinicalrecord.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException.Reason;

/**
 * A patient's request for a copy of their whole clinical record, and what became of it (D-95): who asked —
 * the patient, or a parent for their child under 13 —, for whom, when, how it is delivered, its code, when it
 * was put together, who authenticated it and when, and, on paper, when reception handed it over and who did.
 *
 * <p>It keeps no content. The record only grows — nothing in it is changed or deleted, and everything says
 * when it was written —, so the copy is the record as it stood at {@link #assembledAt()}, put together again
 * from it whenever it is shown ({@link RecordCopyContent}). Putting it together again is moving that time to
 * now, before it is authenticated.
 *
 * <p>A practitioner authenticates it within {@link #AUTHENTICATE_WITHIN} of the request (Ley 26.529, art. 14);
 * one late is authenticated all the same, and said to be late. Plain domain object and immutable: each step
 * answers a new copy, and a refused one leaves it as it was.
 */
public class RecordCopy {

    /** What the law gives to deliver it. Not a setting: it is the law's. */
    public static final Duration AUTHENTICATE_WITHIN = Duration.ofHours(48);

    /** As long as the columns that keep usernames (D-38). */
    public static final int MAX_USERNAME_LENGTH = 100;

    private final RecordCopyId id;
    private final PatientId patientId;
    private final UUID requestedBy;
    private final String requestedByAccount;
    private final LocalDateTime requestedAt;
    private final CopyDelivery delivery;
    private final CopyCode code;
    private final LocalDateTime assembledAt;
    private final int assembly;
    private final Authentication authentication;
    private final Handover handover;

    private RecordCopy(RecordCopyId id, PatientId patientId, UUID requestedBy, String requestedByAccount,
            LocalDateTime requestedAt, CopyDelivery delivery, CopyCode code, LocalDateTime assembledAt, int assembly,
            Authentication authentication, Handover handover) {
        this.id = id;
        this.patientId = patientId;
        this.requestedBy = requestedBy;
        this.requestedByAccount = requestedByAccount;
        this.requestedAt = requestedAt;
        this.delivery = delivery;
        this.code = code;
        this.assembledAt = assembledAt;
        this.assembly = assembly;
        this.authentication = authentication;
        this.handover = handover;
    }

    /** Who authenticated it — the practitioner, and the user they logged in as: their signature — and when. */
    public record Authentication(PractitionerId practitionerId, String username, LocalDateTime at) {

        public Authentication {
            if (practitionerId == null || at == null || !fits(username)) {
                throw new IllegalArgumentException("An authentication needs the practitioner, their user and when");
            }
        }
    }

    /** When reception handed the envelope over, and which user of reception did. */
    public record Handover(String username, LocalDateTime at) {

        public Handover {
            if (at == null || !fits(username)) {
                throw new IllegalArgumentException("A handover needs reception's user and when");
            }
        }
    }

    /**
     * A new request, put together as the record stands now.
     *
     * @param requestedBy the patient who asked: the patient themselves, or their parent
     * @param requestedByAccount the name the asking account goes by wherever something records who did it (D-81)
     */
    public static RecordCopy request(RecordCopyId id, PatientId patientId, UUID requestedBy,
            String requestedByAccount, LocalDateTime now, CopyDelivery delivery, CopyCode code) {
        return restore(id, patientId, requestedBy, requestedByAccount, now, delivery, code, now, 1, null, null);
    }

    /** A copy read back from storage, checked the same way. Public because the storage adapter lives elsewhere. */
    public static RecordCopy restore(RecordCopyId id, PatientId patientId, UUID requestedBy,
            String requestedByAccount, LocalDateTime requestedAt, CopyDelivery delivery, CopyCode code,
            LocalDateTime assembledAt, int assembly, Authentication authentication, Handover handover) {
        if (id == null || patientId == null || requestedBy == null || requestedAt == null || delivery == null
                || code == null || assembledAt == null || !fits(requestedByAccount)) {
            throw new IllegalArgumentException(
                    "A copy of a record needs an id, a patient, who asked and when, its delivery, code and assembly");
        }
        if (assembledAt.isBefore(requestedAt) || assembly < 1) {
            throw new IllegalArgumentException("A copy is put together when it is asked for, or later");
        }
        if (authentication != null && authentication.at().isBefore(assembledAt)) {
            throw new IllegalArgumentException("A copy is authenticated after it is put together");
        }
        if (handover != null && (authentication == null || delivery != CopyDelivery.PAPER
                || handover.at().isBefore(authentication.at()))) {
            throw new IllegalArgumentException("Only a copy on paper, once authenticated, is handed over");
        }
        return new RecordCopy(id, patientId, requestedBy, requestedByAccount, requestedAt, delivery, code, assembledAt,
                assembly, authentication, handover);
    }

    private static boolean fits(String username) {
        return username != null && !username.isBlank() && username.length() <= MAX_USERNAME_LENGTH;
    }

    /**
     * The copy put together again, as the record stands now — after a practitioner uploaded what was missing. The
     * time left does not start again.
     *
     * @throws RecordCopyRefusedException once it is authenticated
     */
    public RecordCopy reassembled(LocalDateTime now) {
        if (isAuthenticated()) {
            throw new RecordCopyRefusedException(Reason.ALREADY_AUTHENTICATED);
        }
        return new RecordCopy(id, patientId, requestedBy, requestedByAccount, requestedAt, delivery, code,
                notBefore(now, assembledAt), assembly + 1, null, null);
    }

    /**
     * Authenticated by a practitioner who reviewed it: their login is their electronic signature (Ley 25.506,
     * art. 5; Decreto 1089/2012, art. 13). Whether they may read the record is the use case's to ask.
     *
     * @param reviewedAssembly the assembly they reviewed, so that one put together again since is not
     *        authenticated unseen
     * @param reviewed whether they said they reviewed that it is complete
     * @throws RecordCopyRefusedException when it was authenticated already, put together again since, or not
     *         reviewed
     */
    public RecordCopy authenticated(PractitionerId practitionerId, String username, LocalDateTime now,
            int reviewedAssembly, boolean reviewed) {
        if (isAuthenticated()) {
            throw new RecordCopyRefusedException(Reason.ALREADY_AUTHENTICATED);
        }
        if (reviewedAssembly != assembly) {
            throw new RecordCopyRefusedException(Reason.REASSEMBLED_SINCE);
        }
        if (!reviewed) {
            throw new RecordCopyRefusedException(Reason.NOT_REVIEWED);
        }
        return new RecordCopy(id, patientId, requestedBy, requestedByAccount, requestedAt, delivery, code, assembledAt,
                assembly, new Authentication(practitionerId, username, notBefore(now, assembledAt)), null);
    }

    /**
     * Handed over by reception, in its sealed envelope, to whoever showed their document.
     *
     * @throws RecordCopyRefusedException when it is not on paper, not authenticated, handed over already, or
     *         nobody's document was seen
     */
    public RecordCopy handedOver(String username, LocalDateTime now, boolean documentSeen) {
        if (delivery != CopyDelivery.PAPER) {
            throw new RecordCopyRefusedException(Reason.OTHER_DELIVERY);
        }
        if (!isAuthenticated()) {
            throw new RecordCopyRefusedException(Reason.NOT_AUTHENTICATED);
        }
        if (handover != null) {
            throw new RecordCopyRefusedException(Reason.ALREADY_HANDED_OVER);
        }
        if (!documentSeen) {
            throw new RecordCopyRefusedException(Reason.DOCUMENT_NOT_SEEN);
        }
        return new RecordCopy(id, patientId, requestedBy, requestedByAccount, requestedAt, delivery, code, assembledAt,
                assembly, authentication, new Handover(username, notBefore(now, authentication.at())));
    }

    /**
     * Refuses unless the patient may download it now: a PDF, authenticated.
     *
     * @throws RecordCopyRefusedException when it goes on paper or is not authenticated yet
     */
    public void requireDownloadable() {
        if (delivery != CopyDelivery.PDF) {
            throw new RecordCopyRefusedException(Reason.OTHER_DELIVERY);
        }
        if (!isAuthenticated()) {
            throw new RecordCopyRefusedException(Reason.NOT_AUTHENTICATED);
        }
    }

    /** Never before the time given: a clock set back cannot put a step before the one it follows. */
    private static LocalDateTime notBefore(LocalDateTime now, LocalDateTime than) {
        return now.isBefore(than) ? than : now;
    }

    /** By when it is to be authenticated: {@link #AUTHENTICATE_WITHIN} after it was asked for. */
    public LocalDateTime deadline() {
        return requestedAt.plus(AUTHENTICATE_WITHIN);
    }

    /** What is left until the deadline; negative once it has passed. */
    public Duration timeLeft(LocalDateTime now) {
        return Duration.between(now, deadline());
    }

    /** Whether it was authenticated after its deadline, or, not authenticated yet, the deadline has passed. */
    public boolean isLate(LocalDateTime now) {
        LocalDateTime when = authentication == null ? now : authentication.at();
        return when.isAfter(deadline());
    }

    public boolean isAuthenticated() {
        return authentication != null;
    }

    /** Authenticated, on paper, and not handed over yet: an envelope waiting at reception. */
    public boolean awaitsHandover() {
        return delivery == CopyDelivery.PAPER && isAuthenticated() && handover == null;
    }

    public RecordCopyId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    /** The patient who asked: the patient themselves, or their parent. */
    public UUID requestedBy() {
        return requestedBy;
    }

    public String requestedByAccount() {
        return requestedByAccount;
    }

    public LocalDateTime requestedAt() {
        return requestedAt;
    }

    public CopyDelivery delivery() {
        return delivery;
    }

    public CopyCode code() {
        return code;
    }

    /** The time the record is copied as it stood. */
    public LocalDateTime assembledAt() {
        return assembledAt;
    }

    /** How many times it was put together: 1 when asked for, one more each time again. */
    public int assembly() {
        return assembly;
    }

    public Optional<Authentication> authentication() {
        return Optional.ofNullable(authentication);
    }

    public Optional<Handover> handover() {
        return Optional.ofNullable(handover);
    }
}
