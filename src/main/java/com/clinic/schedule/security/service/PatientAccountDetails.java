package com.clinic.schedule.security.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.clinic.schedule.portal.api.LoginAccount;
import com.clinic.schedule.security.api.PatientUsername;

/**
 * A patient logged in to their web account, as Spring Security holds them (D-80), with the patient it is
 * theirs.
 *
 * <p>Its name is {@link PatientUsername}'s — the account's id —, never the document the patient logged in
 * with: whatever records who did something reads the name, and a document there would say who the patient
 * is to anyone reading the record, the admins included.
 */
public class PatientAccountDetails implements UserDetails {

    /** The only authority a patient has: they reach the patients' routes and nothing of the staff's. */
    public static final String PATIENT = "ROLE_PATIENT";

    private final UUID accountId;
    private final UUID patientId;
    private final String passwordHash;
    private final boolean locked;

    PatientAccountDetails(LoginAccount account) {
        this.accountId = account.accountId();
        this.patientId = account.patientId();
        this.passwordHash = account.passwordHash();
        this.locked = account.locked();
    }

    UUID accountId() {
        return accountId;
    }

    UUID patientId() {
        return patientId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(PATIENT));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return PatientUsername.of(accountId);
    }

    /** Refused before the password is looked at, while too many failed logins keep it locked (D-86). */
    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }
}
