package com.consultorio.agenda.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Dos cadenas de filtros, por el doble consumidor previsto (D-02):
 * <ul>
 *   <li>{@code /api/**}: autenticacion basica, sin sesion y sin CSRF. Es el contrato que
 *       manana consumiran las SPA.</li>
 *   <li>Resto: formulario de login con sesion y CSRF, para las vistas Thymeleaf.</li>
 * </ul>
 * La autorizacion por rol no se resuelve aca sino en los servicios (D-04): recepcion puede
 * consultar cualquier profesional, al profesional se le impone el suyo.
 */
@Configuration
@EnableWebSecurity
class ConfiguracionDeSeguridad {

    @Bean
    @Order(1)
    SecurityFilterChain cadenaApi(HttpSecurity http, AuthenticationEntryPoint errorNoAutenticado)
            throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(e -> e.authenticationEntryPoint(errorNoAutenticado))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cadenaVistas(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").permitAll())
                .logout(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
