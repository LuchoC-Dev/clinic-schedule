package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.RegisteredChild;
import com.clinic.schedule.portal.domain.Registrant;

/** A child of a registration made on the web as a row of {@code web_registration_child}, kept with it (D-92). */
@Embeddable
class WebRegistrationChildRow {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 40)
    private String documentNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FamilyMember.Relation relation;

    protected WebRegistrationChildRow() {
        // required by JPA
    }

    static WebRegistrationChildRow from(RegisteredChild child) {
        Registrant person = child.person();
        WebRegistrationChildRow row = new WebRegistrationChildRow();
        row.fullName = person.fullName();
        row.documentType = person.documentType();
        row.documentNumber = person.documentNumber();
        row.birthDate = person.birthDate();
        row.relation = child.relation();
        return row;
    }

    RegisteredChild toChild() {
        return new RegisteredChild(new Registrant(fullName, documentType, documentNumber, birthDate, null), relation);
    }
}
