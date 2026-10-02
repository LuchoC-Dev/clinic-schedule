package com.clinic.schedule.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.api.PatientAccountReader;

/**
 * Where Spring Security looks a patient up when they log in to their web account (D-80): by their document,
 * typed as its type and number with a space between — "DNI 30111222" —, which the patient module finds, and
 * then their account, if it is in use. Never by the internal id.
 *
 * <p>Two lookups in two modules rather than one in the portal: the patient module asks the portal whether a
 * patient has an account, so the portal asking the patient module back would close a cycle.
 */
@Service
public class PatientAccountDetailsService implements UserDetailsService {

    private final PatientReader patients;
    private final PatientAccountReader accounts;

    PatientAccountDetailsService(PatientReader patients, PatientAccountReader accounts) {
        this.patients = patients;
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String document) {
        String[] parts = document == null ? new String[0] : document.strip().split(" ", 2);
        if (parts.length < 2) {
            throw new UsernameNotFoundException("A document is its type and its number");
        }
        return patients.findIdByDocument(parts[0], parts[1])
                .flatMap(accounts::findActiveForLogin)
                .map(PatientAccountDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No web account in use for that document"));
    }
}
