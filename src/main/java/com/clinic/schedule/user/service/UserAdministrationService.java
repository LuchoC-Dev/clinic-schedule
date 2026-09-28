package com.clinic.schedule.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.security.api.CurrentUserReader;
import com.clinic.schedule.user.api.Role;
import com.clinic.schedule.user.api.UserDeactivated;
import com.clinic.schedule.user.entity.AppUser;
import com.clinic.schedule.user.entity.AppUserChange;
import com.clinic.schedule.user.entity.PasswordPolicy;
import com.clinic.schedule.user.persistence.AppUserChangeRepository;
import com.clinic.schedule.user.persistence.AppUserRepository;

/**
 * What an admin does with the practice's users (D-68): creates them, deactivates and reactivates
 * them, and resets their passwords. Each action is saved with its entry in the history, in the same
 * transaction, stamped with the practice's clock and the admin who did it.
 *
 * <p>The rules that need more than the user itself live here: a username is never used twice, not
 * even after its user was deactivated; a practitioner has one active user at most; an admin does not
 * deactivate themselves. They are checked before saving so the answer is a clear refusal; the
 * database guarantees the first two under a race (D-09).
 *
 * <p>Passwords arrive in plain text and leave hashed: the user never holds one.
 */
@Service
public class UserAdministrationService {

    private final AppUserRepository users;
    private final AppUserChangeRepository changes;
    private final PractitionerReader practitioners;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserReader currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserAdministrationService(AppUserRepository users, AppUserChangeRepository changes,
            PractitionerReader practitioners, PasswordEncoder passwordEncoder, CurrentUserReader currentUser,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.changes = changes;
        this.practitioners = practitioners;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param practitionerId the practitioner a practitioner's user stands for: one with no active user
     * @throws UsernameTakenException when the username is or was someone else's
     * @throws UnknownPractitionerException when there is no such practitioner
     * @throws PractitionerTakenException when the practitioner has an active user already
     * @throws IllegalArgumentException when a value is missing or malformed
     */
    @Transactional
    public AppUser create(String username, Role role, UUID practitionerId, String password) {
        String hash = passwordEncoder.encode(PasswordPolicy.check(password));
        AppUser user = AppUser.create(username, hash, role, practitionerId);
        if (users.existsByUsername(user.getUsername())) {
            throw new UsernameTakenException(user.getUsername());
        }
        if (practitionerId != null) {
            requirePractitioner(practitionerId);
            requireNoActiveUser(practitionerId);
        }
        AppUser saved = users.save(user);
        changes.save(saved.created(now(), currentUser.username()));
        return saved;
    }

    /**
     * The user loses access at once: their open sessions are ended once this is saved.
     *
     * @throws SelfDeactivationException when an admin tries it on themselves
     */
    @Transactional
    public AppUser deactivate(UUID id) {
        AppUser user = byId(id);
        String admin = currentUser.username();
        if (user.getUsername().equals(admin)) {
            throw new SelfDeactivationException();
        }
        changes.save(user.deactivate(now(), admin));
        AppUser saved = users.save(user);
        events.publishEvent(new UserDeactivated(saved.getUsername()));
        return saved;
    }

    /** @throws PractitionerTakenException when the practitioner was given another user meanwhile */
    @Transactional
    public AppUser reactivate(UUID id) {
        AppUser user = byId(id);
        if (user.getPractitionerId() != null && !user.isActive()) {
            requireNoActiveUser(user.getPractitionerId());
        }
        changes.save(user.reactivate(now(), currentUser.username()));
        return users.save(user);
    }

    @Transactional
    public AppUser resetPassword(UUID id, String password) {
        AppUser user = byId(id);
        String hash = passwordEncoder.encode(PasswordPolicy.check(password));
        changes.save(user.resetPassword(hash, now(), currentUser.username()));
        return users.save(user);
    }

    /** Every user, active or not, by username. */
    @Transactional(readOnly = true)
    public List<AppUser> all() {
        return users.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public AppUser byId(UUID id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /** What admins did to the user, the most recent first. */
    @Transactional(readOnly = true)
    public List<AppUserChange> history(UUID id) {
        if (!users.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        return changes.findByUserIdOrderByChangedAtDesc(id);
    }

    /** The practitioners a new practitioner's user may stand for: those with no active user. */
    @Transactional(readOnly = true)
    public List<PractitionerSummary> practitionersWithoutUser() {
        return practitioners.findAll().stream()
                .filter(practitioner -> !users.existsByPractitionerIdAndActiveTrue(practitioner.id()))
                .toList();
    }

    private void requirePractitioner(UUID practitionerId) {
        boolean known = practitioners.findAll().stream().anyMatch(practitioner -> practitioner.id().equals(practitionerId));
        if (!known) {
            throw new UnknownPractitionerException(practitionerId);
        }
    }

    private void requireNoActiveUser(UUID practitionerId) {
        if (users.existsByPractitionerIdAndActiveTrue(practitionerId)) {
            throw new PractitionerTakenException(practitionerId);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
