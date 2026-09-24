package com.clinic.schedule.security.presentation.screen;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The login form the screen chain points at. Spring Security handles the POST. */
@Controller
class LoginViewController {

    @GetMapping("/login")
    String login() {
        return "login";
    }
}
