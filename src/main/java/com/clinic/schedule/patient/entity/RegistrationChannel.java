package com.clinic.schedule.patient.entity;

/**
 * Where a patient was registered (D-91): at reception, or by themselves on the web. Given when they are registered
 * and never changed afterwards, not even once reception verifies them.
 */
public enum RegistrationChannel {
    RECEPTION,
    WEB
}
