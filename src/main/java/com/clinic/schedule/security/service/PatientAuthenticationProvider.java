package com.clinic.schedule.security.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.clinic.schedule.portal.api.PatientLoginWriter;

/**
 * Logs patients in with their document and password, as Spring Security does (D-80), and tells the portal of
 * each login to an account in use, so failed ones in a row lock it for a while (D-86). A document with no
 * account has nothing to count, and a locked account is refused before its password is looked at, so trying
 * does not lengthen the lock. The staff's logins are not counted: theirs is another provider (D-68).
 *
 * <p>Not a bean: Spring Boot would make a lone provider bean everyone's, the staff's logins included. Each of the
 * patients' filter chains makes its own.
 */
public class PatientAuthenticationProvider extends DaoAuthenticationProvider {

    private final PatientAccountDetailsService patients;
    private final PatientLoginWriter logins;

    public PatientAuthenticationProvider(PatientAccountDetailsService patients, PasswordEncoder passwordEncoder,
            PatientLoginWriter logins) {
        super(patients);
        setPasswordEncoder(passwordEncoder);
        this.patients = patients;
        this.logins = logins;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication authenticated;
        try {
            authenticated = super.authenticate(authentication);
        } catch (BadCredentialsException e) {
            failed(authentication.getName());
            throw e;
        }
        logins.loginSucceeded(((PatientAccountDetails) authenticated.getPrincipal()).accountId());
        return authenticated;
    }

    private void failed(String document) {
        try {
            logins.loginFailed(((PatientAccountDetails) patients.loadUserByUsername(document)).accountId());
        } catch (UsernameNotFoundException e) {
            // No account in use for that document: nothing to count.
        }
    }
}
