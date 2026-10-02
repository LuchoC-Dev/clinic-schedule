package com.clinic.schedule.portal.adapter.out.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.domain.ActivationMessage;
import com.clinic.schedule.portal.domain.BookingConfirmation;

/**
 * Sends a web account's messages by email, over the same SMTP server as the reminders (D-78, D-79), from
 * the practice's sender. The links point at {@code app.portal.base-url}, where the patients reach the
 * application.
 */
@Component
class EmailAccountAdapter implements AccountMailPort {

    private final JavaMailSender mail;
    private final ActivationText text;
    private final BookingConfirmationText confirmationText;
    private final String from;

    EmailAccountAdapter(JavaMailSender mail, @Value("${app.mail.from}") String from,
            @Value("${app.practice.name}") String practice, @Value("${app.practice.address}") String address,
            @Value("${app.practice.phone}") String phone, @Value("${app.portal.base-url}") String baseUrl) {
        this.mail = mail;
        this.from = from;
        this.text = new ActivationText(practice, address, phone, baseUrl);
        this.confirmationText = new BookingConfirmationText(practice, address, phone);
    }

    @Override
    public void sendActivation(ActivationMessage message) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from);
        email.setTo(message.address());
        email.setSubject(text.subject());
        email.setText(text.plain(message));
        mail.send(email);
    }

    @Override
    public void sendBookingConfirmation(BookingConfirmation confirmation) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from);
        email.setTo(confirmation.address());
        email.setSubject(confirmationText.subject());
        email.setText(confirmationText.plain(confirmation));
        mail.send(email);
    }
}
