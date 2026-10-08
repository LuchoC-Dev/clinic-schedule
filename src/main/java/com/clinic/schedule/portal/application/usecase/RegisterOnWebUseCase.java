package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import com.clinic.schedule.portal.application.port.AccountNamePort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.application.port.RegistrantPort;
import com.clinic.schedule.portal.application.port.WebRegistrationRepository;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.AccountOpeningService;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.AgeBand;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.RegisteredChild;
import com.clinic.schedule.portal.domain.Registrant;
import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.clinic.schedule.portal.domain.RegistrationRefusedMessage;
import com.clinic.schedule.portal.domain.WebRegistration;
import com.clinic.schedule.portal.domain.WebRegistrationPolicy;
import com.clinic.schedule.shared.PasswordPolicy;

/**
 * An adult registers themselves on the web, with their children under 13 if they want, and confirms it from the
 * link sent to their email (D-91, D-92); until then nobody is a patient, nor has an account, and a registration
 * never confirmed is forgotten once it expires, after {@code app.portal.registration-valid-for}.
 *
 * <p>Nobody learns from the web whether a document is a patient's. What can be told from the form alone — a value
 * that cannot be right, an adult under 18 or a child of 13, a document typed twice — is said; then an adult whose
 * document the practice knows already keeps nothing and gets an email asking them to come to reception, and any
 * other gets the link, with the children whose document the practice did not know, and the answer is the same.
 * Both hash the password and send one email, so neither takes longer. A sending that fails keeps nothing, and says
 * so whichever it was.
 *
 * <p>Confirming registers the adult with their account in use and, in a family group of theirs, each child it can,
 * with the relation not proved until reception sees what proves it (D-92).
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
    private final AccountNamePort accountNames;
    private final RegisterChildUseCase children;
    private final AccountMailPort mail;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration validFor;

    public RegisterOnWebUseCase(RegistrantPort registrants, WebRegistrationRepository registrations,
            AccountHolderPort holders, PatientAccountRepository accounts, AccountChangeRepository changes,
            AccountNamePort accountNames, RegisterChildUseCase children, AccountMailPort mail,
            PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.portal.registration-valid-for}") Duration validFor) {
        this.registrants = registrants;
        this.registrations = registrations;
        this.holders = holders;
        this.accounts = accounts;
        this.changes = changes;
        this.accountNames = accountNames;
        this.children = children;
        this.mail = mail;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.validFor = validFor;
    }

    /**
     * @return whether the email went out; nothing is kept when it did not
     * @throws com.clinic.schedule.portal.application.exception.InvalidRegistrationException when a name, a document,
     *         a date of birth or the phone cannot be right
     * @throws IllegalArgumentException when a date of birth or a relation is missing, there are too many children, a
     *         document is there twice, or the email or the password cannot be one
     * @throws com.clinic.schedule.portal.domain.exception.AccountRefusedException when the adult is not 18 or older
     * @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when a child is 13 or older
     */
    @Transactional
    public boolean request(Command command) {
        LocalDateTime now = LocalDateTime.now(clock);
        Registrant registrant = new Registrant(strip(command.fullName()), strip(command.documentType()),
                strip(command.documentNumber()), command.birthDate(), strip(command.phoneNumber()));
        List<RegisteredChild> typed = command.children().stream().map(RegisterOnWebUseCase::child).toList();
        registrants.check(registrant);
        typed.forEach(child -> registrants.check(child.person()));
        String passwordHash = passwordEncoder.encode(PasswordPolicy.check(command.password()));
        WebRegistrationPolicy.check(registrant, typed, now.toLocalDate());
        List<RegisteredChild> kept = new ArrayList<>();
        List<String> notRegistered = new ArrayList<>();
        for (RegisteredChild child : typed) {
            if (registrants.isKnown(child.person())) {
                notRegistered.add(child.fullName());
            } else {
                kept.add(child);
            }
        }
        WebRegistration.Issued issued = WebRegistrationPolicy.request(registrant, kept, command.email(), passwordHash,
                now, validFor);
        WebRegistration registration = issued.stored();
        if (registrants.isKnown(registrant)) {
            return send(() -> mail.sendRegistrationRefused(
                    new RegistrationRefusedMessage(registration.email(), registrant.fullName())));
        }
        boolean sent = send(() -> mail.sendRegistration(new RegistrationMessage(registration.email(),
                registrant.fullName(), issued.token(), registration.expiresAt(),
                kept.stream().map(RegisteredChild::fullName).toList(), notRegistered)));
        if (sent) {
            registrations.save(registration);
        }
        return sent;
    }

    private static RegisteredChild child(ChildCommand child) {
        return new RegisteredChild(new Registrant(strip(child.fullName()), strip(child.documentType()),
                strip(child.documentNumber()), child.birthDate(), null), child.relation());
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
     * Registers the adult, unverified and marked as registered on the web, with their account in use, and each child
     * it can in a family group with them, and forgets the registration. A document someone registered meanwhile — at
     * reception, or confirming a registration of their own first — registers nothing: the adult's, nobody at all,
     * a child's, that child, who is named for the adult to go to reception; so is a child who turned 13 meanwhile.
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
            return new Confirmation(Outcome.DOCUMENT_KNOWN, List.of());
        }
        AccountHolder holder = holders.find(patientId.get())
                .orElseThrow(() -> new IllegalStateException("No patient for the registration just confirmed"));
        AccountUpdate opened = AccountOpeningService.openRegistered(holder,
                accounts.findByPatientId(holder.patientId()).isPresent(), registration.email(),
                registration.passwordHash(), now);
        accounts.save(opened.account());
        changes.save(opened.change());

        List<RegisteredChild> registering = new ArrayList<>();
        List<String> notRegistered = new ArrayList<>();
        for (RegisteredChild child : registration.children()) {
            // A document someone registered meanwhile is told by registering it: it registers nothing.
            if (AgeBand.of(child.person().birthDate(), now.toLocalDate()) == AgeBand.CHILD) {
                registering.add(child);
            } else {
                notRegistered.add(child.fullName());
            }
        }
        if (!registering.isEmpty()) {
            notRegistered.addAll(children.registerWithAdult(holder, registering,
                    accountNames.nameOf(opened.account().id()), now));
        }
        return new Confirmation(Outcome.REGISTERED, notRegistered);
    }

    /** Forgets the registrations that expired never confirmed, their children with them. @return how many */
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

    /** What the adult typed: the phone may be left out, and the children are none unless given. */
    public record Command(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            String phoneNumber, String email, String password, List<ChildCommand> children) {

        public Command {
            children = children == null ? List.of() : List.copyOf(children);
        }

        public Command(String fullName, String documentType, String documentNumber, LocalDate birthDate,
                String phoneNumber, String email, String password) {
            this(fullName, documentType, documentNumber, birthDate, phoneNumber, email, password, List.of());
        }
    }

    /** A child under 13 the adult registers with them, and what the adult is to them. */
    public record ChildCommand(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            FamilyMember.Relation relation) {
    }

    /** What confirming did, and the children it could not register, by name. */
    public record Confirmation(Outcome outcome, List<String> notRegistered) {
    }

    public enum Outcome {
        /** The adult is a patient, with their account in use, and the children it could register in their group. */
        REGISTERED,
        /** The adult's document was someone's by then: nothing was registered, and they are sent to reception. */
        DOCUMENT_KNOWN
    }
}
