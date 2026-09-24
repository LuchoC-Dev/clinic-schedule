package com.clinic.schedule.consultation;

import java.time.Period;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clinic.schedule.consultation.domain.ClinicalRecordAccessPolicy;

/**
 * Wiring for the module. The policy is declared here instead of being annotated, so the domain
 * keeps no reference to Spring (D-16), and its period comes from configuration (D-49).
 */
@Configuration
class ConsultationConfiguration {

    @Bean
    ClinicalRecordAccessPolicy clinicalRecordAccessPolicy(
            @Value("${app.clinical-record.access-period}") String accessPeriod) {
        return new ClinicalRecordAccessPolicy(Period.parse(accessPeriod));
    }
}
