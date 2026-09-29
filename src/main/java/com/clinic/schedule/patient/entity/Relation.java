package com.clinic.schedule.patient.entity;

/**
 * Who the person a phone or an email belongs to is to the patient, when it is not the patient's own
 * (D-73): a child's parents or guardian, an elderly patient's partner or child, or someone else.
 */
public enum Relation {
    MOTHER, FATHER, GUARDIAN, PARTNER, CHILD, OTHER
}
