package com.clinic.schedule.cashdesk.adapter.out.external;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.cashdesk.application.port.InsuredPatientPort;
import com.clinic.schedule.cashdesk.domain.Insurance;
import com.clinic.schedule.cashdesk.domain.Insured;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * Answers {@link InsuredPatientPort} by asking the patient module through its api (D-90): its summary of a
 * patient already says whether their identity was verified, and their health insurance (D-60, D-74).
 */
@Component
class InsuredPatientAdapter implements InsuredPatientPort {

    private final PatientReader patients;

    InsuredPatientAdapter(PatientReader patients) {
        this.patients = patients;
    }

    @Override
    public Insured insuredOf(UUID patientId) {
        return patients.findAll(List.of(patientId)).stream()
                .findFirst()
                .map(InsuredPatientAdapter::insured)
                .orElse(new Insured(false, null));
    }

    private static Insured insured(PatientSummary patient) {
        PatientSummary.Insurance insurance = patient.insurance();
        return new Insured(patient.identityVerified(), insurance == null || insurance.insurer() == null ? null
                : new Insurance(insurance.insurer(), insurance.memberNumber(), insurance.plan()));
    }
}
