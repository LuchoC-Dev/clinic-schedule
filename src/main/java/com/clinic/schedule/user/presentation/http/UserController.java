package com.clinic.schedule.user.presentation.http;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinic.schedule.user.entity.AppUser;
import com.clinic.schedule.user.service.UserAdministrationService;

/**
 * The admins' endpoints for the practice's users (D-68): create, list, deactivate, reactivate,
 * reset a password, and what was done to each.
 *
 * <p>Deactivating, reactivating and resetting are business operations, each recorded, so they are
 * {@code POST} on a sub-path (D-23). There is no delete and no edit: a user is never deleted, and
 * their username, role and practitioner never change.
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserAdministrationService users;

    UserController(UserAdministrationService users) {
        this.users = users;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        AppUser created = users.create(request.username(), request.role(), request.practitionerId(), request.password());
        return ResponseEntity.created(URI.create("/api/users/" + created.getId())).body(UserResponse.of(created));
    }

    /** Every user, active or not, by username. */
    @GetMapping
    List<UserResponse> all() {
        return users.all().stream().map(UserResponse::of).toList();
    }

    @GetMapping("/{id}")
    UserResponse byId(@PathVariable UUID id) {
        return UserResponse.of(users.byId(id));
    }

    @PostMapping("/{id}/deactivate")
    UserResponse deactivate(@PathVariable UUID id) {
        return UserResponse.of(users.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    UserResponse reactivate(@PathVariable UUID id) {
        return UserResponse.of(users.reactivate(id));
    }

    @PostMapping("/{id}/password-reset")
    UserResponse resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
        return UserResponse.of(users.resetPassword(id, request.password()));
    }

    /**
     * The practitioners a new practitioner's user may stand for: those with no active user (D-68). The admins'
     * screen offers these alone, and the rule stays here (D-107).
     */
    @GetMapping("/practitioners-without-user")
    List<FreePractitionerResponse> practitionersWithoutUser() {
        return users.practitionersWithoutUser().stream()
                .map(practitioner -> new FreePractitionerResponse(practitioner.id(), practitioner.fullName()))
                .toList();
    }

    /** What admins did to the user, the most recent first. */
    @GetMapping("/{id}/changes")
    List<UserChangeResponse> changes(@PathVariable UUID id) {
        return users.history(id).stream().map(UserChangeResponse::of).toList();
    }
}
