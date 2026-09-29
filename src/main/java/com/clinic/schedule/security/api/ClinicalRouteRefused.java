package com.clinic.schedule.security.api;

/**
 * A clinical route was refused to a logged-in user by their role (D-72): reception, the admin or the
 * waiting room asking for a record, a file, a consultation, a check or an order. Published as it is
 * refused, for the module whose content it is to record it.
 *
 * @param username who was refused
 * @param method the request's method
 * @param path the request's path, without the query
 */
public record ClinicalRouteRefused(String username, String method, String path) {
}
