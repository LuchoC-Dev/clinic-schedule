package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.AccessGrantRepository;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/** Implements the port over JPA. The only place that knows access grants live in a table. */
@Repository
class AccessGrantJpaAdapter implements AccessGrantRepository {

    private final AccessGrantRowRepository rows;

    AccessGrantJpaAdapter(AccessGrantRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public List<AccessGrant> findAllForPatient(PatientId patientId) {
        return rows.findByPatientId(patientId.value()).stream().map(AccessGrantRow::toGrant).toList();
    }

    @Override
    public List<AccessGrant> findFor(PatientId patientId, PractitionerId practitionerId) {
        return rows.findByPatientIdAndPractitionerId(patientId.value(), practitionerId.value()).stream()
                .map(AccessGrantRow::toGrant)
                .toList();
    }

    /** Only ever a new grant: nothing changes one once it is granted. */
    @Override
    public void save(AccessGrant grant) {
        rows.save(AccessGrantRow.from(grant));
    }
}
