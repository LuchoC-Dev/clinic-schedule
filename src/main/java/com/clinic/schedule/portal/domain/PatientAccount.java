package com.clinic.schedule.portal.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.clinic.schedule.portal.domain.exception.AccountStateException;
import com.clinic.schedule.portal.domain.exception.InvalidAccountEmailException;

/**
 * A patient's web account (D-79): theirs alone, one at most, logged into with their document and the
 * password they chose on activating it.
 *
 * <p>Its email is its own, apart from the patient's contact email: it is where the activation goes, and the
 * account's other messages — a booking's confirmation, the link to recover the password —, never a reminder.
 * Only its form is checked, as the patient's contact email's is, and it is kept in lower case. It is not
 * unique: a family may share one.
 *
 * <p>Opened by {@link AccountOpeningService} alone, which checks the patient may have one; every other
 * change is the account's own to guard. It is immutable: each change returns the account as it became, with
 * the entry that records it, so a refused one leaves nothing half done and none is made without its entry.
 */
public final class PatientAccount {

    /** The longest address the standards allow, and the length of the column. */
    static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final UUID id;
    private final UUID patientId;
    private final String email;
    private final String passwordHash;
    private final AccountStatus status;
    private final LocalDateTime openedAt;
    private final int failedLogins;
    private final LocalDateTime lockedUntil;

    private PatientAccount(UUID id, UUID patientId, String email, String passwordHash, AccountStatus status,
            LocalDateTime openedAt, int failedLogins, LocalDateTime lockedUntil) {
        if (id == null || patientId == null || status == null || openedAt == null) {
            throw new IllegalArgumentException("An account needs its patient, its state and when it was opened");
        }
        if (failedLogins < 0) {
            throw new IllegalArgumentException("Failed logins are counted from none");
        }
        if (status == AccountStatus.PENDING && passwordHash != null) {
            throw new IllegalArgumentException("A pending account has no password yet");
        }
        if (status == AccountStatus.ACTIVE && passwordHash == null) {
            throw new IllegalArgumentException("An active account has a password");
        }
        this.id = id;
        this.patientId = patientId;
        this.email = email(email);
        this.passwordHash = passwordHash;
        this.status = status;
        this.openedAt = openedAt;
        this.failedLogins = failedLogins;
        this.lockedUntil = lockedUntil;
    }

    /** A new account, waiting for the patient to choose their password. Only the opening service calls it. */
    static AccountUpdate open(UUID patientId, String email, LocalDateTime at, String by) {
        PatientAccount account = new PatientAccount(UUID.randomUUID(), patientId, email, null, AccountStatus.PENDING,
                at, 0, null);
        return new AccountUpdate(account, AccountChange.of(account.id, AccountAction.OPENED, at, by));
    }

    /**
     * A new account in use at once, with the password the patient chose on registering on the web and the email
     * that confirming the registration proved theirs (D-91). Only the opening service calls it.
     *
     * @param passwordHash the password already hashed
     */
    static AccountUpdate registered(UUID patientId, String email, String passwordHash, LocalDateTime at) {
        requirePassword(passwordHash);
        PatientAccount account = new PatientAccount(UUID.randomUUID(), patientId, email, passwordHash,
                AccountStatus.ACTIVE, at, 0, null);
        return new AccountUpdate(account, AccountChange.of(account.id, AccountAction.REGISTERED, at, null));
    }

    /**
     * An account as it was stored.
     *
     * @param failedLogins the failed logins in a row since the last that went through or the last lock
     * @param lockedUntil until when logging in is refused; null, or past, when it is not
     */
    public static PatientAccount restore(UUID id, UUID patientId, String email, String passwordHash,
            AccountStatus status, LocalDateTime openedAt, int failedLogins, LocalDateTime lockedUntil) {
        return new PatientAccount(id, patientId, email, passwordHash, status, openedAt, failedLogins, lockedUntil);
    }

    /**
     * The entry that records an activation email going out, made only once it did.
     *
     * @throws AccountStateException unless the account is waiting to be activated
     */
    public AccountChange activationSent(LocalDateTime at, String by) {
        requirePending();
        return AccountChange.of(id, AccountAction.ACTIVATION_SENT, at, by);
    }

    /**
     * The patient chooses their password, and the account is in use from then on.
     *
     * @param passwordHash the password already hashed: an account never holds the password itself
     * @throws AccountStateException unless the account is waiting to be activated
     */
    public AccountUpdate activate(String passwordHash, LocalDateTime at) {
        requirePending();
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("An account is activated with a password");
        }
        return update(new PatientAccount(id, patientId, email, passwordHash, AccountStatus.ACTIVE, openedAt,
                failedLogins, lockedUntil),
                AccountAction.ACTIVATED, at, null);
    }

    /**
     * Reception changes the email, with the patient's document in sight; nothing when it is the same one.
     * Whether a pending account gets a new activation there is the use case's to do.
     */
    public Optional<AccountUpdate> changeEmail(String email, LocalDateTime at, String by) {
        String address = email(email);
        if (address.equals(this.email)) {
            return Optional.empty();
        }
        return Optional.of(update(new PatientAccount(id, patientId, address, passwordHash, status, openedAt,
                failedLogins, lockedUntil),
                AccountAction.EMAIL_CHANGED, at, by));
    }

    /** @throws AccountStateException when the account is inactive already */
    public AccountUpdate deactivate(LocalDateTime at, String by) {
        if (status == AccountStatus.INACTIVE) {
            throw new AccountStateException("The account is inactive already");
        }
        return update(new PatientAccount(id, patientId, email, passwordHash, AccountStatus.INACTIVE, openedAt,
                failedLogins, lockedUntil),
                AccountAction.DEACTIVATED, at, by);
    }

    /**
     * Back in use if the patient had chosen a password, or waiting for it again if they never had.
     *
     * @throws AccountStateException unless the account is inactive
     */
    public AccountUpdate reactivate(LocalDateTime at, String by) {
        if (status != AccountStatus.INACTIVE) {
            throw new AccountStateException("The account is not inactive");
        }
        AccountStatus back = passwordHash == null ? AccountStatus.PENDING : AccountStatus.ACTIVE;
        return update(new PatientAccount(id, patientId, email, passwordHash, back, openedAt, failedLogins, lockedUntil),
                AccountAction.REACTIVATED, at, by);
    }

    /** Whether logging in is refused for now, after too many failed logins in a row (D-86). */
    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * A login with a wrong password: counted, and the one that makes {@code attempts} in a row locks the
     * account for {@code lockFor}, recorded. Not counted while it is locked, so trying does not lengthen the lock.
     */
    public FailedLogin loginFailed(LocalDateTime now, int attempts, Duration lockFor) {
        if (isLockedAt(now)) {
            return new FailedLogin(this, null);
        }
        if (failedLogins + 1 < attempts) {
            return new FailedLogin(withLogins(failedLogins + 1, null), null);
        }
        return new FailedLogin(withLogins(0, now.plus(lockFor)), AccountChange.of(id, AccountAction.LOCKED, now, null));
    }

    /** A login that went through starts the count again; nothing when there is nothing to clear. */
    public Optional<PatientAccount> loginSucceeded() {
        return failedLogins == 0 && lockedUntil == null ? Optional.empty() : Optional.of(withLogins(0, null));
    }

    /**
     * The entry that records a link to choose a new password going out, made only once it did.
     *
     * @throws AccountStateException unless the account is in use
     */
    public AccountChange recoverySent(LocalDateTime at) {
        requireActive();
        return AccountChange.of(id, AccountAction.RECOVERY_SENT, at, null);
    }

    /**
     * The patient chooses a new password from the link in the email. It also lifts a lock: they proved the
     * account's email is theirs.
     *
     * @param passwordHash the password already hashed
     * @throws AccountStateException unless the account is in use
     */
    public AccountUpdate resetPassword(String passwordHash, LocalDateTime at) {
        requireActive();
        requirePassword(passwordHash);
        return update(new PatientAccount(id, patientId, email, passwordHash, status, openedAt, 0, null),
                AccountAction.PASSWORD_RESET, at, null);
    }

    /**
     * The patient changes their password, logged in; whether they gave the current one is the use case's to check.
     *
     * @param passwordHash the password already hashed
     * @throws AccountStateException unless the account is in use
     */
    public AccountUpdate changePassword(String passwordHash, LocalDateTime at) {
        requireActive();
        requirePassword(passwordHash);
        return update(new PatientAccount(id, patientId, email, passwordHash, status, openedAt, failedLogins,
                lockedUntil), AccountAction.PASSWORD_CHANGED, at, null);
    }

    /**
     * The entry that records the patient asking to register a child under 13 from their account (D-92), whatever
     * came of it: the history is what tells how many they asked for in a day.
     *
     * @throws AccountStateException unless the account is in use
     */
    public AccountChange childRequested(LocalDateTime at) {
        requireActive();
        return AccountChange.of(id, AccountAction.CHILD_REQUESTED, at, null);
    }

    private PatientAccount withLogins(int failed, LocalDateTime until) {
        return new PatientAccount(id, patientId, email, passwordHash, status, openedAt, failed, until);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountStateException("The account is not in use");
        }
    }

    private static void requirePassword(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("A password is required");
        }
    }

    private AccountUpdate update(PatientAccount changed, AccountAction action, LocalDateTime at, String by) {
        return new AccountUpdate(changed, AccountChange.of(id, action, at, by));
    }

    private void requirePending() {
        if (status != AccountStatus.PENDING) {
            throw new AccountStateException("The account is not waiting to be activated");
        }
    }

    /**
     * The address as an account keeps it: in lower case, once its form is checked.
     *
     * @throws InvalidAccountEmailException when it cannot be one
     */
    static String email(String email) {
        String address = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (address.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidAccountEmailException("An email is at most " + MAX_EMAIL_LENGTH + " characters");
        }
        if (!EMAIL.matcher(address).matches()) {
            throw new InvalidAccountEmailException("An email looks like name@domain.com");
        }
        return address;
    }

    public UUID id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public String email() {
        return email;
    }

    /** Null until the patient chooses a password. */
    public String passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public LocalDateTime openedAt() {
        return openedAt;
    }

    public int failedLogins() {
        return failedLogins;
    }

    /** Null, or past, when logging in is not refused. */
    public LocalDateTime lockedUntil() {
        return lockedUntil;
    }
}
