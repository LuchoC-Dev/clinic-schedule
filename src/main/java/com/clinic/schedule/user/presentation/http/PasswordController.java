package com.clinic.schedule.user.presentation.http;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinic.schedule.user.service.PasswordChangeService;

/**
 * Whoever sends it changes their own password (D-69). The only route open to a user whose password is
 * temporary; every role reaches it, since every user has a password.
 */
@RestController
class PasswordController {

    private final PasswordChangeService passwords;

    PasswordController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    @PostMapping("/api/password")
    ResponseEntity<Void> change(@Valid @RequestBody PasswordChangeRequest request) {
        passwords.change(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
