package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.InvalidRegistrationLinkException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.application.port.RegistrantPort;
import com.clinic.schedule.portal.application.port.WebRegistrationRepository;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.AccountOpeningService;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.Registrant;
import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.clinic.schedule.portal.domain.RegistrationRefusedMessage;
import com.clinic.schedule.portal.domain.WebRegistration;
import com.clinic.schedule.portal.domain.WebRegistrationPolicy;
import com.clinic.schedule.shared.PasswordPolicy;

/**
 * An adult registers themselves on the web, and confirms it from the link sent to their email (D-91); until then
 * nobody is a patient, nor has an account, and a registration never confirmed is forgotten once it expires, after
 * {@code app.portal.registration-valid-for}.
 *
 * <p>Nobody learns from the web whether a document is a patient's. What can be told from the form alone — a value
 * that cannot be right, someone under 18 — is said; then a document the practice knows already keeps nothing and
 * gets an email asking them to come to reception, and any other gets the link, and the answer is the same. Both
 * hash the password and send one email, so neither takes longer. A sending that fails keeps nothing, and says so
 * whichever it was.
 *
 * <p>Nobody is logged in: the link is what says whose registration it is.
 */
@Service
public class RegisterOnWebUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterOnWebUseCase.class);

    private final RegistrantPort registrants;
    private final WebRegistrationRepository registrations;
    private final AccountHolderPort holders;
    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final AccountMailPort mail;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration validFor;

    public RegisterOnWebUseCase(RegistrantPort registrants, WebRegistrationRepository registrations,
            AccountHolderPort holders, PatientAccountRepository accounts, AccountChangeRepository changes,
            AccountMailPort mail, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.portal.registration-valid-for}") Duration validFor) {
        this.registrants = registrants;
        this.registrations = registrations;
        this.holders = holders;
        this.accounts = accounts;
        this.changes = changes;
        this.mail = mail;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.validFor = validFor;
    }

    /**
     * @return whether the email went out; nothing is kept when it did not
     * @throws com.clinic.schedule.portal.application.exception.InvalidRegistrationException when the name, the
     *         document, the date of birth or the phone cannot be right
     * @throws IllegalArgumentException when there is no date of birth, or the email or the password cannot be one
     * @throws com.clinic.schedule.portal.domain.exception.AccountRefusedException when they are not 18 or older
     */
    @Transactional
    public boolean request(Command command) {
        LocalDateTime now = LocalDateTime.now(clock);
        Registrant registrant = new Registrant(strip(command.fullName()), strip(command.documentType()),
                strip(command.documentNumber()), command.birthDate(), strip(command.phoneNumber()));
        registrants.check(registrant);
        String passwordHash = passwordEncoder.encode(PasswordPolicy.check(command.password()));
        WebRegistration.Issued issued = WebRegistrationPolicy.request(registrant, command.email(), passwordHash, now,
                validFor);
        WebRegistration registration = issued.stored();
        if (registrants.isKnown(registrant)) {
            return send(() -> mail.sendRegistrationRefused(
                    new RegistrationRefusedMessage(registration.email(), registrant.fullName())));
        }
        boolean sent = send(() -> mail.sendRegistration(new RegistrationMessage(registration.email(),
                registrant.fullName(), issued.token(), registration.expiresAt())));
        if (sent) {
            registrations.save(registration);
        }
        return sent;
    }

    private static boolean send(Runnable sending) {
        try {
            sending.run();
            return true;
        } catch (RuntimeException e) {
            log.warn("The email answering a registration on the web did not go out: {}", e.getMessage());
            return false;
        }
    }

    /** Whether the link would confirm a registration, for the screen to offer it or say it will not. */
    @Transactional(readOnly = true)
    public boolean isUsable(String token) {
        return waiting(token, LocalDateTime.now(clock)).isPresent();
    }

    /**
     * Registers the patient, unverified and marked as registered on the web, with their account in use, and
     * forgets the registration. A document someone registered meanwhile — at reception, or confirming a
     * registration of their own first — registers nothing, and the registration is forgotten all the same.
     *
     * @throws InvalidRegistrationLinkException when the link confirms nothing
     */
    @Transactional
    public Confirmation confirm(String token) {
        LocalDateTime now = LocalDateTime.now(clock);
        WebRegistration registration = waiting(token, now).orElseThrow(InvalidRegistrationLinkException::new);
        registrations.delete(registration.id());
        Optional<UUID> patientId = registrants.register(registration.registrant());
        if (patientId.isEmpty()) {
            return Confirmation.DOCUMENT_KNOWN;
        }
        AccountHolder holder = holders.find(patientId.get())
                .orElseThrow(() -> new IllegalStateException("No patient for the registration just confirmed"));
        AccountUpdate opened = AccountOpeningService.openRegistered(holder,
                accounts.findByPatientId(holder.patientId()).isPresent(), registration.email(),
                registration.passwordHash(), now);
        accounts.save(opened.account());
        changes.save(opened.change());
        return Confirmation.REGISTERED;
    }

    /** Forgets the registrations that expired never confirmed. @return how many */
    @Transactional
    public int forgetExpired() {
        return registrations.deleteExpiredAt(LocalDateTime.now(clock));
    }

    private Optional<WebRegistration> waiting(String token, LocalDateTime now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return registrations.findByHash(WebRegistration.hashOf(token)).filter(stored -> !stored.isExpiredAt(now));
    }

    private static String strip(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** What the adult typed: the phone may be left out. */
    public record Command(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            String phoneNumber, String email, String password) {
    }

    /** What confirming did. */
    public enum Confirmation {
        /** They are a patient, with their account in use. */
        REGISTERED,
        /** The document was someone's by then: nothing was registered, and they are sent to reception. */
        DOCUMENT_KNOWN
    }
}
