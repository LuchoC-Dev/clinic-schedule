package com.clinic.schedule.user.presentation.screen;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.user.api.Role;
import com.clinic.schedule.user.entity.AppUser;
import com.clinic.schedule.user.entity.PasswordPolicy;
import com.clinic.schedule.user.entity.UserStatusException;
import com.clinic.schedule.user.service.PractitionerTakenException;
import com.clinic.schedule.user.service.SelfDeactivationException;
import com.clinic.schedule.user.service.UnknownPractitionerException;
import com.clinic.schedule.user.service.UserAdministrationService;
import com.clinic.schedule.user.service.UserNotFoundException;
import com.clinic.schedule.user.service.UsernameTakenException;

/**
 * The admins' screens (D-68): the users, creating one, and each user's screen to deactivate,
 * reactivate or reset their password, with what admins did to them.
 *
 * <p>Thin and throwaway, as D-02 requires: it calls {@link UserAdministrationService}, the same the
 * REST controller calls, and holds no rule. The practitioners' names come from the practitioner
 * module's api. Refusals come back as messages in Spanish (D-13).
 */
@Controller
class UserViewController {

    private final UserAdministrationService users;
    private final PractitionerReader practitioners;

    UserViewController(UserAdministrationService users, PractitionerReader practitioners) {
        this.users = users;
        this.practitioners = practitioners;
    }

    /** The admins' screens show the admins' header, which offers nothing but the users. */
    @ModelAttribute("admin")
    boolean admin() {
        return true;
    }

    @GetMapping("/users")
    String users(Model model) {
        Map<UUID, String> names = practitionerNames();
        model.addAttribute("users", users.all().stream().map(user -> UserEntry.of(user, names)).toList());
        model.addAttribute("roles", Arrays.stream(Role.values()).map(role -> Map.entry(role, UserEntry.role(role))).toList());
        model.addAttribute("freePractitioners", users.practitionersWithoutUser());
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        return "users";
    }

    @PostMapping("/users")
    String create(@RequestParam String username, @RequestParam(required = false) Role role,
            @RequestParam(required = false) UUID practitionerId, @RequestParam String password,
            RedirectAttributes flash) {
        try {
            AppUser created = users.create(username, role, role == Role.PRACTITIONER ? practitionerId : null, password);
            flash.addFlashAttribute("message", "Usuario creado: " + created.getUsername() + ".");
            return "redirect:/users/" + created.getId();
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", refusal(e));
            flash.addFlashAttribute("username", username);
        }
        return "redirect:/users";
    }

    @GetMapping("/users/{id}")
    String user(@PathVariable UUID id, Model model) {
        try {
            model.addAttribute("user", UserEntry.of(users.byId(id), practitionerNames()));
            model.addAttribute("history", users.history(id).stream().map(UserChangeEntry::of).toList());
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        return "user";
    }

    @PostMapping("/users/{id}/deactivate")
    String deactivate(@PathVariable UUID id, RedirectAttributes flash) {
        return act(id, flash, "Usuario desactivado: ya no puede ingresar.", () -> users.deactivate(id));
    }

    @PostMapping("/users/{id}/reactivate")
    String reactivate(@PathVariable UUID id, RedirectAttributes flash) {
        return act(id, flash, "Usuario reactivado.", () -> users.reactivate(id));
    }

    @PostMapping("/users/{id}/password-reset")
    String resetPassword(@PathVariable UUID id, @RequestParam String password, RedirectAttributes flash) {
        return act(id, flash, "Contraseña blanqueada. Pasásela al usuario.", () -> users.resetPassword(id, password));
    }

    private String act(UUID id, RedirectAttributes flash, String done, Runnable action) {
        try {
            action.run();
            flash.addFlashAttribute("message", done);
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", refusal(e));
        }
        return "redirect:/users/" + id;
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }

    /** What the admin reads when the action is refused; anything else is not a refusal and goes on. */
    private static String refusal(RuntimeException e) {
        if (e instanceof UsernameTakenException) {
            return "Ese nombre de usuario ya se usó. Un nombre de usuario no se repite, aunque el anterior esté desactivado.";
        }
        if (e instanceof PractitionerTakenException) {
            return "Ese profesional ya tiene un usuario activo.";
        }
        if (e instanceof UnknownPractitionerException) {
            return "Ese profesional no existe.";
        }
        if (e instanceof SelfDeactivationException) {
            return "No podés desactivar tu propio usuario.";
        }
        if (e instanceof UserStatusException) {
            return "El usuario ya estaba así.";
        }
        if (e instanceof IllegalArgumentException) {
            return "Revisá los datos: el usuario lleva de 3 a 50 minúsculas, números, puntos, guiones o guiones bajos; "
                    + "el rol es obligatorio, y el de un profesional pide elegir el profesional; la contraseña, de "
                    + PasswordPolicy.MIN_LENGTH + " a " + PasswordPolicy.MAX_BYTES + " caracteres.";
        }
        throw e;
    }
}
