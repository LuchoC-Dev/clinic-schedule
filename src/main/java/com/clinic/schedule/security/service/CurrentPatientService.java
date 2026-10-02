package com.clinic.schedule.security.service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.clinic.schedule.security.api.CurrentPatientReader;

/** Answers {@link CurrentPatientReader} from the security context of the current request. */
@Service
class CurrentPatientService implements CurrentPatientReader {

    @Override
    public UUID patientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PatientAccountDetails patient) {
            return patient.patientId();
        }
        throw new IllegalStateException("No patient is logged in");
    }
}
