package com.clinic.schedule.portal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.domain.ActivationMessage;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.clinic.schedule.portal.domain.PasswordResetMessage;
import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.clinic.schedule.portal.domain.RegistrationRefusedMessage;

/**
 * Keeps the web accounts' emails instead of sending them; an address that starts with "falla" cannot be
 * reached. Imported by every test of the accounts, so they share one application context.
 */
@TestConfiguration
public class KeptAccountMail {

    public static final List<ActivationMessage> SENT = new CopyOnWriteArrayList<>();
    public static final List<BookingConfirmation> CONFIRMATIONS = new CopyOnWriteArrayList<>();
    public static final List<PasswordResetMessage> RESETS = new CopyOnWriteArrayList<>();
    public static final List<RegistrationMessage> REGISTRATIONS = new CopyOnWriteArrayList<>();
    public static final List<RegistrationRefusedMessage> REGISTRATIONS_REFUSED = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    AccountMailPort keptAccountMail() {
        return new AccountMailPort() {
            @Override
            public void sendActivation(ActivationMessage message) {
                reach(message.address());
                SENT.add(message);
            }

            @Override
            public void sendBookingConfirmation(BookingConfirmation confirmation) {
                reach(confirmation.address());
                CONFIRMATIONS.add(confirmation);
            }

            @Override
            public void sendPasswordReset(PasswordResetMessage message) {
                reach(message.address());
                RESETS.add(message);
            }

            @Override
            public void sendRegistration(RegistrationMessage message) {
                reach(message.address());
                REGISTRATIONS.add(message);
            }

            @Override
            public void sendRegistrationRefused(RegistrationRefusedMessage message) {
                reach(message.address());
                REGISTRATIONS_REFUSED.add(message);
            }
        };
    }

    private static void reach(String address) {
        if (address.startsWith("falla")) {
            throw new IllegalStateException("The mail server is down");
        }
    }
}
