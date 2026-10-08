package com.clinic.schedule.portal.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException.Reason;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;

/**
 * Who may register themselves on the web, and the only way to make a registration that waits for its
 * confirmation (D-17, D-91):
 *
 * <ul>
 *   <li>a date of birth, by which they are 18 or older on the day: a younger patient is registered at reception,
 *       or by their parents (phase 8, part 2);</li>
 *   <li>an email that can be the account's, kept in lower case as the account keeps it;</li>
 *   <li>up to {@value #MAX_CHILDREN} children under 13 on the day, each with a date of birth and the relation, and no
 *       document twice, the adult's included (D-92). One registered later from the account is checked alike.</li>
 * </ul>
 *
 * <p>Whether the name, the document and the phone can be a patient's is the patient module's to say, and whether
 * the document is someone's already is never told on the web: neither is looked at here. It is handed what it
 * needs and reads nothing itself.
 */
public final class WebRegistrationPolicy {

    /** How many children an adult registers with them: each one is a document looked up on the web. */
    static final int MAX_CHILDREN = 5;

    private WebRegistrationPolicy() {
    }

    /**
     * Whether an adult may register, with those children: every child they typed is checked, whether it is kept or
     * not, so the answer never depends on whether the practice knows a document.
     *
     * @throws IllegalArgumentException when a date of birth is missing, there are too many children, or a document
     *         is there twice
     * @throws AccountRefusedException when the adult is not 18 or older
     * @throws FamilyRefusedException when a child is 13 or older
     */
    public static void check(Registrant registrant, List<RegisteredChild> children, LocalDate today) {
        if (registrant.birthDate() == null) {
            throw new IllegalArgumentException("Registering on the web needs a date of birth");
        }
        if (!AccountOpeningService.isAdultOn(registrant.birthDate(), today)) {
            throw new AccountRefusedException(Reason.UNDER_EIGHTEEN,
                    "Only a patient of " + AccountOpeningService.MIN_AGE_ON_THE_WEB + " or older registers on the web");
        }
        if (children.size() > MAX_CHILDREN) {
            throw new IllegalArgumentException("An adult registers up to " + MAX_CHILDREN + " children with them");
        }
        Set<String> documents = new HashSet<>();
        documents.add(registrant.documentKey());
        for (RegisteredChild child : children) {
            checkChild(child, today);
            if (!documents.add(child.person().documentKey())) {
                throw new IllegalArgumentException("A document is registered once");
            }
        }
    }

    /**
     * A child under 13 on the day, with a date of birth: with the adult's registration, or later from their account.
     *
     * @throws IllegalArgumentException when the date of birth is missing
     * @throws FamilyRefusedException when they are 13 or older, who register at reception and decide for themselves
     */
    public static void checkChild(RegisteredChild child, LocalDate today) {
        if (child.person().birthDate() == null) {
            throw new IllegalArgumentException("A child is registered with their date of birth");
        }
        if (AgeBand.of(child.person().birthDate(), today) != AgeBand.CHILD) {
            throw new FamilyRefusedException(FamilyRefusedException.Reason.NOT_A_CHILD,
                    "A child registered on the web is under 13: from 13 they register at reception");
        }
    }

    /**
     * The registration, with the children to keep: those whose document the practice did not know.
     *
     * @param children those to keep, among those {@link #check} was given
     * @param passwordHash the password chosen, already hashed
     * @param validFor how long the link to confirm it lasts
     * @throws IllegalArgumentException when the adult or a child may not register, or the email cannot be one
     * @throws AccountRefusedException when the adult is not 18 or older
     * @throws FamilyRefusedException when a child is 13 or older
     */
    public static WebRegistration.Issued request(Registrant registrant, List<RegisteredChild> children, String email,
            String passwordHash, LocalDateTime now, Duration validFor) {
        check(registrant, children, now.toLocalDate());
        return WebRegistration.issue(registrant, children, PatientAccount.email(email), passwordHash, now, validFor);
    }
}
