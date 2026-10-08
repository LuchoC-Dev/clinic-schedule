package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import com.clinic.schedule.portal.domain.Registrant;
import com.clinic.schedule.portal.domain.WebRegistration;

/**
 * A registration made on the web as a row while it waits for its confirmation (D-91), keeping only the hash of the
 * link's token and of the password, with the children registered with it in the order they were typed (D-92).
 */
@Entity
@Table(name = "web_registration")
class WebRegistrationRow {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 40)
    private String documentNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "web_registration_child", joinColumns = @JoinColumn(name = "registration_id"))
    @OrderColumn(name = "position")
    private List<WebRegistrationChildRow> children = new ArrayList<>();

    protected WebRegistrationRow() {
        // required by JPA
    }

    static WebRegistrationRow from(WebRegistration registration) {
        Registrant registrant = registration.registrant();
        WebRegistrationRow row = new WebRegistrationRow();
        row.id = registration.id();
        row.tokenHash = registration.hash();
        row.fullName = registrant.fullName();
        row.documentType = registrant.documentType();
        row.documentNumber = registrant.documentNumber();
        row.birthDate = registrant.birthDate();
        row.phoneNumber = registrant.phoneNumber();
        row.email = registration.email();
        row.passwordHash = registration.passwordHash();
        row.requestedAt = registration.requestedAt();
        row.expiresAt = registration.expiresAt();
        row.children = new ArrayList<>(registration.children().stream().map(WebRegistrationChildRow::from).toList());
        return row;
    }

    WebRegistration toRegistration() {
        return new WebRegistration(id, tokenHash,
                new Registrant(fullName, documentType, documentNumber, birthDate, phoneNumber),
                children.stream().map(WebRegistrationChildRow::toChild).toList(), email, passwordHash, requestedAt,
                expiresAt);
    }
}
