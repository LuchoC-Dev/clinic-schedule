package com.clinic.schedule.security;

import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.clinic.schedule.portal.api.PatientLoginWriter;
import com.clinic.schedule.security.presentation.TemporaryPasswordFilter;
import com.clinic.schedule.security.presentation.http.ApiSecurityErrorHandler;
import com.clinic.schedule.security.presentation.http.SessionHandlers;
import com.clinic.schedule.security.presentation.screen.DocumentLoginFilter;
import com.clinic.schedule.security.presentation.screen.ScreenAccessDeniedHandler;
import com.clinic.schedule.security.service.AppUserDetails;
import com.clinic.schedule.security.service.AppUserDetailsService;
import com.clinic.schedule.security.service.PatientAccountDetailsService;
import com.clinic.schedule.security.service.PatientAuthenticationProvider;
import com.clinic.schedule.user.api.Role;

/**
 * Two filter chains, one per kind of client (D-02, D-34):
 *
 * <ul>
 *   <li>{@code /api/**}: the contract of the single-page applications, which log in with the screens' session,
 *       answered in JSON, and send its CSRF token with every write (D-97); any other client authenticates every
 *       request with Basic authentication, with no session and no token.</li>
 *   <li>everything else: a login form, a session, and CSRF protection for the screens, and the applications'
 *       own files, open to anyone, since what they show is asked of the API.</li>
 * </ul>
 *
 * <p>The patients have the same two of their own, ahead of the staff's, under {@code /api/portal/**} and
 * {@code /portal/**} (D-80): they log in with their document, on a login of their own, and reach nothing of
 * the staff's, as the staff reach nothing of theirs. Each chain names whom it looks up, since there are two
 * kinds of people to log in. A patient's session keeps its login under a key of its own, so one browser
 * logged in on both sides keeps both.
 *
 * <p>What each role may reach is decided here, by route: a practitioner reads the agenda and
 * works with clinical records, which are theirs alone (D-51); an admin administers the users and
 * reads who read which record and who was refused one, the cancellations and the day's cash desk
 * without their patients, and the practitioners' list to name them, and reaches nothing else (D-68, D-71, D-72, D-76,
 * D-87, D-107); reception does
 * everything else, the cash desk included, which a practitioner never reaches. Every user changes their own password, and one whose password is
 * temporary does nothing else until they do (D-69). The waiting room's user sees the waiting-room
 * screen and its stream, and nothing else (D-70). A clinical route refused to a role is said, to be
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
    /** Where a session keeps the staff's login: Spring Security's own key, which the screens use too. */
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
     * <p>The patients' application logs in here, with a session, at {@code /api/portal/session} (D-97): the
     * login of the screens, answered in JSON, its session kept under the screens' key and registered in their
     * registry, so either side's login is the other's and a deactivated account loses it at once (D-79, D-80).
     * Basic authentication still answers any other client, on every request and with no session.
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
                        .requestMatchers(HttpMethod.GET, "/api/portal/session").permitAll()
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
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    /**
     * The login form asks for the document as a type and a number, which {@link DocumentLoginFilter} joins.
     * Registered sessions, as the staff's, so a deactivated account loses its own at once (D-79) — in a
     * registry of their own: a registry keeps one login for each session, and a browser where someone of the
     * staff logged in after the patient would otherwise hide the patient's.
     */
    @Bean
    @Order(2)
    SecurityFilterChain portalScreenChain(HttpSecurity http,
            @Qualifier("patientSessionRegistry") SessionRegistry sessions, ScreenAccessDeniedHandler denied,
            PatientAccountDetailsService patients, PasswordEncoder passwordEncoder, PatientLoginWriter attempts)
            throws Exception {
        return http
                .securityMatcher("/portal/**")
                .authenticationProvider(new PatientAuthenticationProvider(patients, passwordEncoder, attempts))
                .securityContext(context -> context.securityContextRepository(sessionKeptUnder(PORTAL_LOGIN)))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/portal/app", "/portal/app/**").permitAll()
                        .requestMatchers("/portal/login", "/portal/activation", "/portal/cancel", "/portal/cancel/done",
                                "/portal/password-recovery", "/portal/password-reset", "/portal/register",
                                "/portal/register/sent", "/portal/register/confirm").permitAll()
                        .anyRequest().hasRole(PATIENT))
                .addFilterBefore(new DocumentLoginFilter("/portal/login"), UsernamePasswordAuthenticationFilter.class)
                .formLogin(login -> login.loginPage("/portal/login").defaultSuccessUrl("/portal/appointments", true)
                        .permitAll())
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredUrl("/portal/login?expired"))
                .logout(logout -> logout.logoutUrl("/portal/logout").logoutSuccessUrl("/portal/login?logout"))
                .exceptionHandling(handling -> handling.accessDeniedHandler(denied))
                .build();
    }

    /**
     * The staff's application logs in here, with a session, at {@code /api/session} (D-97): the login of the
     * screens, answered in JSON, its session the screens' own and registered in their registry, so either
     * side's login is the other's and a deactivated user loses it at once (D-68). Basic authentication still
     * answers any other client, on every request and with no session (D-34).
     */
    @Bean
    @Order(3)
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
                        // The waiting room's screen, as on the screens (D-70, D-109).
                        .requestMatchers(HttpMethod.GET, "/api/waiting-room").hasAnyRole(RECEPTION, WAITING_ROOM)
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(TemporaryPasswordFilter.forApi(errors), AuthorizationFilter.class)
                .formLogin(login -> login.loginProcessingUrl("/api/session")
                        .successHandler(handlers.staffLoggedIn()).failureHandler(handlers.staffLoginFailed()))
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredSessionStrategy(handlers.sessionEnded()))
                .logout(logout -> logout.logoutUrl("/api/session/logout").logoutSuccessHandler(handlers.loggedOut()))
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain screenChain(HttpSecurity http, @Qualifier("sessionRegistry") SessionRegistry sessions,
            ScreenAccessDeniedHandler denied, AppUserDetailsService users) throws Exception {
        return http
                .userDetailsService(users)
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/login", "/error", "/app", "/app/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/queue-events").hasAnyRole(RECEPTION, PRACTITIONER, WAITING_ROOM)
                        .requestMatchers(HttpMethod.GET, "/waiting-room").hasAnyRole(RECEPTION, WAITING_ROOM)
                        .requestMatchers(HttpMethod.GET, "/", "/agenda").hasAnyRole(RECEPTION, PRACTITIONER)
                        .requestMatchers("/consultations/**", "/checks/**", "/orders/**", "/attachments/**",
                                "/clinical-records/**", "/my-queue/**", "/record-copies/**").hasRole(PRACTITIONER)
                        .requestMatchers("/users/**", "/record-readings", "/record-refusals", "/cancellation-log",
                                "/cash-desk-log").hasRole(ADMIN)
                        .requestMatchers("/password").authenticated()
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(TemporaryPasswordFilter.forScreens(), AuthorizationFilter.class)
                .formLogin(login -> login.loginPage("/login").successHandler(landing()).permitAll())
                // Every session is registered, with no limit to how many a user has, so the sessions
                // of a deactivated user can be ended at once (D-68).
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredUrl("/login?expired"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(handling -> handling.accessDeniedHandler(denied))
                .build();
    }

    /**
     * Each role lands on its own screen: the admin on the users, the waiting room on its screen,
     * everyone else on the agenda. A user whose password is temporary lands on changing it.
     *
     * <p>The waiting room's session does not run out while nobody touches it: nobody does, it is a
     * screen on the wall, and an open stream is not activity for the session (D-70).
     */
    private static AuthenticationSuccessHandler landing() {
        String admin = "ROLE_" + ADMIN; // the prefix hasRole adds
        String waitingRoom = "ROLE_" + WAITING_ROOM;
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
            if (authorities.contains(waitingRoom)) {
                request.getSession().setMaxInactiveInterval(-1); // never, for the servlet container
            }
            String screen = authorities.contains(AppUserDetails.TEMPORARY_PASSWORD) ? "/password?required"
                    : authorities.contains(admin) ? "/users"
                    : authorities.contains(waitingRoom) ? "/waiting-room" : "/agenda";
            response.sendRedirect(request.getContextPath() + screen);
        };
    }

    /**
     * A login kept in the session under its key, for every request after it; or, for a request with Basic
     * authentication, for that request alone, so it opens no session (D-97).
     */
    private static SecurityContextRepository sessionKeptUnder(String key) {
        HttpSessionSecurityContextRepository session = new HttpSessionSecurityContextRepository();
        session.setSpringSecurityContextKey(key);
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(), session);
    }

    /**
     * The API asks a CSRF token of a browser's writes (D-97): those that come with a session's cookie, which is
     * how the applications are logged in, and their login itself, so no other site logs a browser in as someone else.
     * A write with an {@code Authorization} header is not asked: it cannot come from another site's page, since no
     * browser adds the header there by itself — the API never asks for Basic credentials (D-34) — and a page cannot
     * add it to a request to another origin without CORS, which the application does not allow. Nor is a write
     * with neither a session's cookie nor credentials: it is nobody's, and refused as such where a login is
     * needed.
     */
    private static RequestMatcher browserWrites(String login) {
        return request -> !SAFE_METHODS.contains(request.getMethod())
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null
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
