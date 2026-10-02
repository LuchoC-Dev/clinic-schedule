package com.clinic.schedule.portal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.domain.ActivationMessage;

/**
 * Keeps the web accounts' emails instead of sending them; an address that starts with "falla" cannot be
 * reached. Imported by every test of the accounts, so they share one application context.
 */
@TestConfiguration
public class KeptAccountMail {

    public static final List<ActivationMessage> SENT = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    AccountMailPort keptAccountMail() {
        return message -> {
            if (message.address().startsWith("falla")) {
                throw new IllegalStateException("The mail server is down");
            }
            SENT.add(message);
        };
    }
}
