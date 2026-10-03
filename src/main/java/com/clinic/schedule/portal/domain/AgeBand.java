package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.time.Period;

/**
 * How old a member of a family group is, as the group's rules count it (D-84): a child under 13, whose parents
 * act for them (Código Civil y Comercial, art. 26); an adolescent from 13 to 17, who decides alone whether their
 * family sees their appointments; and an adult, who also acts for the members who share theirs.
 */
public enum AgeBand {
    CHILD,
    ADOLESCENT,
    ADULT;

    /** The age from which a patient decides for themselves. */
    static final int ADOLESCENT_FROM = 13;
    /** The age from which a patient acts for the rest of their family. */
    static final int ADULT_FROM = 18;

    public static AgeBand of(LocalDate birthDate, LocalDate today) {
        int years = Period.between(birthDate, today).getYears();
        return years < ADOLESCENT_FROM ? CHILD : years < ADULT_FROM ? ADOLESCENT : ADULT;
    }
}
