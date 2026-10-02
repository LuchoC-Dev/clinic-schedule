package com.clinic.schedule.reminder.adapter.out.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.clinic.schedule.reminder.application.port.ReminderSenderPort;
import com.clinic.schedule.reminder.domain.ReminderMessage;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends reminders by email, over SMTP (D-78): plain text and HTML, from the practice's sender. Where the
 * SMTP server is comes from {@code spring.mail.*} — in development, Mailpit, which keeps every message and
 * sends none on.
 */
@Component
class EmailReminderAdapter implements ReminderSenderPort {

    private final JavaMailSender mail;
    private final ReminderText text;
    private final String from;

    EmailReminderAdapter(JavaMailSender mail, @Value("${app.mail.from}") String from,
            @Value("${app.practice.name}") String practice, @Value("${app.practice.address}") String address,
            @Value("${app.practice.phone}") String phone) {
        this.mail = mail;
        this.from = from;
        this.text = new ReminderText(practice, address, phone);
    }

    @Override
    public void send(ReminderMessage message) {
        try {
            MimeMessage email = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(email, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.recipient().address());
            helper.setSubject(text.subject());
            helper.setText(text.plain(message), text.html(message));
            mail.send(email);
        } catch (MessagingException e) {
            throw new IllegalStateException("The reminder could not be written: " + e.getMessage(), e);
        }
    }
}
