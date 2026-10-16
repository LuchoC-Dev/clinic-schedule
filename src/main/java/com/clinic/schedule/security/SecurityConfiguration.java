package com.clinic.schedule.security;

import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.clinic.schedule.portal.api.PatientLoginWriter;
import com.clinic.schedule.security.presentation.TemporaryPasswordFilter;
import com.clinic.schedule.security.presentation.http.ApiSecurityErrorHandler;
import com.clinic.schedule.security.presentation.http.DocumentLoginFilter;
import com.clinic.schedule.security.presentation.http.SessionHandlers;
import com.clinic.schedule.security.service.AppUserDetailsService;
import com.clinic.schedule.security.service.PatientAccountDetailsService;
import com.clinic.schedule.security.service.PatientAuthenticationProvider;
import com.clinic.schedule.user.api.Role;

/**
 * Three filter chains (D-34, D-114):
 *
 * <ul>
 *   <li>{@code /api/portal/**}: the patients' API, which their application logs in to with their document, with a
 *       session, answered in JSON, and sends its CSRF token with every write (D-80, D-97);</li>
 *   <li>{@code /api/**}: the staff's API, logged in to the same way by the staff's application with a username;</li>
 *   <li>everything else: the two applications' files and their addresses — the staff's at {@code /}, the
 *       patients' at {@code /portal/} —, open to anyone, since what they show is asked of the API.</li>
 * </ul>
 *
 * <p>The patients reach nothing of the staff's, as the staff reach nothing of theirs (D-80). Each API chain names
 * whom it looks up, since there are two kinds of people to log in. A patient's session keeps its login under a key
 * of its own, so one browser logged in on both sides keeps both.
 *
 * <p>What each role may reach is decided here, by route: a practitioner reads the agenda and
 * works with clinical records, which are theirs alone (D-51); an admin administers the users and
 * reads who read which record and who was refused one, the cancellations and the day's cash desk
 * without their patients, and the practitioners' list to name them, and reaches nothing else (D-68, D-71, D-72, D-76,
 * D-87, D-107); reception does
 * everything else, the cash desk included, which a practitioner never reaches. Every user changes their own password, and one whose password is
 * temporary does nothing else until they do (D-69). The waiting room's user reads the waiting room and
 * the queues' stream, and nothing else (D-70, D-109). A clinical route refused to a role is said, to be
 * recorded (D-72).
 * <em>Whose</em> agenda or record a practitioner reads is not a route question, so it is
 * decided by the use case, which asks the security module who is asking (D-04, D-49).
 */
@Configuration
class SecurityConfiguration {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String RECEPTION = Role.RECEPTION.name();
    private static final String PRACTITIONER = Role.PRACTITIONER.name();
    private static final String ADMIN = Role.ADMIN.name();
    private static final String WAITING_ROOM = Role.WAITING_ROOM.name();
    /** Where a session keeps the staff's login: Spring Security's own key. */
    private static final String STAFF_LOGIN = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    /** Where a session keeps a patient's login, apart from the staff's, so one browser keeps both (D-80). */
    private static final String PORTAL_LOGIN = "PORTAL_SECURITY_CONTEXT";
    /** The role of a patient logged in to their web account, as {@code hasRole} reads it (D-80). */
    private static final String PATIENT = "PATIENT";

    /**
     * Activating an account is done before there is one to log in with, and cancelling from an email's link
     * with nobody logged in (D-83), as asking for a new password and choosing it (D-86), and registering on the web
     * and confirming it (D-91): the link is what says whose. Patients log in through {@link PatientAuthenticationProvider}, which counts their failed logins.
     *
     * <p>The patients' application logs in here, with a session, at {@code /api/portal/session} (D-97): a form
     * login answered in JSON, the document joined by {@link DocumentLoginFilter}, its session registered in the
     * patients' registry, so a deactivated account loses it at once (D-79). A registry of their own, apart from the
     * staff's: a registry keeps one login for each session, and a browser where someone of the staff logged in
     * after the patient would otherwise hide the patient's. It is the only way in: there is no Basic authentication
     * (D-115).
     */
    @Bean
    @Order(1)
    SecurityFilterChain portalApiChain(HttpSecurity http, ApiSecurityErrorHandler errors, SessionHandlers handlers,
            @Qualifier("patientSessionRegistry") SessionRegistry sessions, PatientAccountDetailsService patients,
            PasswordEncoder passwordEncoder, PatientLoginWriter logins) throws Exception {
        return http
                .securityMatcher("/api/portal/**")
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(browserWrites("/api/portal/session")))
                .authenticationProvider(new PatientAuthenticationProvider(patients, passwordEncoder, logins))
                .securityContext(context -> context.securityContextRepository(sessionKeptUnder(PORTAL_LOGIN)))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.POST, "/api/portal/activation", "/api/portal/password-recovery",
                                "/api/portal/password-reset", "/api/portal/registrations",
                                "/api/portal/registrations/confirmation").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/portal/session", "/api/portal/activation/*",
                                "/api/portal/password-reset/*", "/api/portal/registrations/confirmation/*").permitAll()
                        .requestMatchers("/api/portal/cancel-links/**").permitAll()
                        .anyRequest().hasRole(PATIENT))
                .addFilterBefore(new DocumentLoginFilter("/api/portal/session"),
                        UsernamePasswordAuthenticationFilter.class)
                .formLogin(login -> login.loginProcessingUrl("/api/portal/session")
                        .successHandler(handlers.patientLoggedIn()).failureHandler(handlers.patientLoginFailed()))
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredSessionStrategy(handlers.sessionEnded()))
                .logout(logout -> logout.logoutUrl("/api/portal/session/logout")
                        .logoutSuccessHandler(handlers.loggedOut()))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    /**
     * The staff's application logs in here, with a session, at {@code /api/session} (D-97): a form login answered
     * in JSON, every session registered, with no limit to how many a user has, so a deactivated user loses theirs
     * at once (D-68). It is the only way in: there is no Basic authentication (D-115).
     */
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, ApiSecurityErrorHandler errors, SessionHandlers handlers,
            @Qualifier("sessionRegistry") SessionRegistry sessions, AppUserDetailsService users) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(browserWrites("/api/session")))
                .userDetailsService(users)
                .securityContext(context -> context.securityContextRepository(sessionKeptUnder(STAFF_LOGIN)))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/appointments").hasAnyRole(RECEPTION, PRACTITIONER)
                        // A practitioner reads their own agenda, which the use case decides (D-39, D-104).
                        .requestMatchers(HttpMethod.GET, "/api/agenda").hasAnyRole(RECEPTION, PRACTITIONER)
                        // The admins' screens name the practitioners and filter by them; reading the list is all (D-107).
                        .requestMatchers(HttpMethod.GET, "/api/practitioners").hasAnyRole(RECEPTION, ADMIN)
                        .requestMatchers("/api/consultations/**", "/api/checks/**", "/api/orders/**",
                                "/api/attachments/**", "/api/clinical-records/**", "/api/my-queue/**",
                                "/api/record-copies/**", "/api/access-grants/**").hasRole(PRACTITIONER)
                        .requestMatchers("/api/users/**", "/api/record-readings", "/api/record-refusals",
                                "/api/cancellation-log", "/api/cash-desk-log").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/password").authenticated()
                        // The waiting room's screen (D-70, D-109).
                        .requestMatchers(HttpMethod.GET, "/api/waiting-room").hasAnyRole(RECEPTION, WAITING_ROOM)
                        // The queues' stream, which the arrivals, the practitioner's queue and the waiting room follow (D-66).
                        .requestMatchers(HttpMethod.GET, "/api/queue-events")
                                .hasAnyRole(RECEPTION, PRACTITIONER, WAITING_ROOM)
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(new TemporaryPasswordFilter(errors), AuthorizationFilter.class)
                .formLogin(login -> login.loginProcessingUrl("/api/session")
                        .successHandler(handlers.staffLoggedIn()).failureHandler(handlers.staffLoginFailed()))
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredSessionStrategy(handlers.sessionEnded()))
                .logout(logout -> logout.logoutUrl("/api/session/logout").logoutSuccessHandler(handlers.loggedOut()))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    /**
     * The two applications: their files and every address of theirs, open to anyone, since what they show is asked
     * of the API, which decides who may (D-97, D-114). Spring Security still answers them with {@code no-store}.
     */
    @Bean
    @Order(3)
    SecurityFilterChain applicationsChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(routes -> routes.anyRequest().permitAll())
                .build();
    }

    /** A login kept in the session under its key, for every request after it (D-97). */
    private static SecurityContextRepository sessionKeptUnder(String key) {
        HttpSessionSecurityContextRepository session = new HttpSessionSecurityContextRepository();
        session.setSpringSecurityContextKey(key);
        return session;
    }

    /**
     * The API asks a CSRF token of a browser's writes (D-97): those that come with a session's cookie, which is
     * how the applications are logged in, and their login itself, so no other site logs a browser in as someone else.
     * A write without a session's cookie is not asked: it is nobody's, and refused as such where a login is needed
     * (D-115).
     */
    private static RequestMatcher browserWrites(String login) {
        return request -> !SAFE_METHODS.contains(request.getMethod())
                && (request.getRequestedSessionId() != null
                        || login.equals(request.getRequestURI().substring(request.getContextPath().length())));
    }

    /** The staff's open sessions. */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** The patients' open sessions, apart from the staff's (D-80). */
    @Bean
    SessionRegistry patientSessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registries when a session ends, by logging out or running out, so they forget it. */
    @Bean
    static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
