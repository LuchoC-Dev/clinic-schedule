package com.clinic.schedule.user.presentation.screen;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import com.clinic.schedule.shared.PasswordPolicy;
import com.clinic.schedule.user.service.PasswordChangeService;
import com.clinic.schedule.user.service.UnchangedPasswordException;
import com.clinic.schedule.user.service.WrongPasswordException;

/**
 * Changing one's own password, for every user (D-69). A user whose password is temporary is kept on
 * this screen until they change it.
 *
 * <p>Once changed, the session is closed and the user logs in again with the new password: the
 * session held the old state of their password, and starting a new one is simpler than rewriting it.
 */
@Controller
class PasswordViewController {

    private final PasswordChangeService passwords;

    PasswordViewController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    @GetMapping("/password")
    String form(Model model) {
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        return "password";
    }

    @PostMapping("/password")
    String change(@RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String repeatedPassword, HttpServletRequest request, RedirectAttributes flash)
            throws ServletException {
        if (!newPassword.equals(repeatedPassword)) {
            flash.addFlashAttribute("error", "La contraseña nueva y su repetición no coinciden.");
            return "redirect:/password";
        }
        try {
            passwords.change(currentPassword, newPassword);
        } catch (WrongPasswordException e) {
            flash.addFlashAttribute("error", "La contraseña actual no es correcta.");
            return "redirect:/password";
        } catch (UnchangedPasswordException e) {
            flash.addFlashAttribute("error", "La contraseña nueva tiene que ser distinta de la actual.");
            return "redirect:/password";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", "La contraseña nueva lleva de " + PasswordPolicy.MIN_LENGTH + " a "
                    + PasswordPolicy.MAX_BYTES + " caracteres.");
            return "redirect:/password";
        }
        request.logout();
        return "redirect:/login?changed";
    }
}
