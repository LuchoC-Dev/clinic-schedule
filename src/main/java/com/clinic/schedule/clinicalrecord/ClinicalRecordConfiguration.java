package com.clinic.schedule.clinicalrecord;

import java.time.Period;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clinic.schedule.clinicalrecord.domain.AccessGrantPolicy;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;

/**
 * Wiring for the module. The policies are declared here instead of being annotated, so the domain
 * keeps no reference to Spring (D-16), and what may change comes from configuration (D-49, D-54).
 */
@Configuration
class ClinicalRecordConfiguration {

    @Bean
    ClinicalRecordAccessPolicy clinicalRecordAccessPolicy(
            @Value("${app.clinical-record.access-period}") String accessPeriod) {
        return new ClinicalRecordAccessPolicy(Period.parse(accessPeriod));
    }

    /** How long an access grant lasts, and whether asking for one needs a reason (D-54). */
    @Bean
    AccessGrantPolicy accessGrantPolicy(
            @Value("${app.clinical-record.access-grant.period}") String period,
            @Value("${app.clinical-record.access-grant.reason-required}") boolean reasonRequired) {
        return new AccessGrantPolicy(Period.parse(period), reasonRequired);
    }
}
