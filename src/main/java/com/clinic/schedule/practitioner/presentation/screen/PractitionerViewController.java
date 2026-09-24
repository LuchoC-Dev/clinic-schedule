package com.clinic.schedule.practitioner.presentation.screen;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.practitioner.entity.DuplicateReasonException;
import com.clinic.schedule.practitioner.entity.InvalidWeeklyScheduleException;
import com.clinic.schedule.practitioner.entity.Practitioner;
import com.clinic.schedule.practitioner.entity.ReasonNotFoundException;
import com.clinic.schedule.practitioner.entity.TimeOff;
import com.clinic.schedule.practitioner.entity.WorkingHours;
import com.clinic.schedule.practitioner.service.PractitionerNotFoundException;
import com.clinic.schedule.practitioner.service.PractitionerService;

/**
 * Reception's practitioner screens: the list and registering one, and each practitioner's
 * configuration — name, reasons, week — with their coming absences (D-46).
 *
 * <p>Thin and throwaway, as D-02 requires: it calls {@link PractitionerService}, the same the
 * REST controller calls, and holds no rule. The absence form posts to the appointment module,
 * which owns what an absence does to the agenda (D-32); this screen only shows the result.
 *
 * <p>Refusals come back as messages in Spanish (D-13).
 */
@Controller
class PractitionerViewController {

    /** Empty rows offered below the week's stretches, to add new ones. */
    static final int EMPTY_ROWS = 3;

    private static final Locale SPANISH = Locale.of("es", "AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d/MM/yyyy", SPANISH);

    private final PractitionerService practitioners;
    private final Clock clock;

    PractitionerViewController(PractitionerService practitioners, Clock clock) {
        this.practitioners = practitioners;
        this.clock = clock;
    }

    @GetMapping("/practitioners")
    String list(Model model) {
        model.addAttribute("practitioners", practitioners.all());
        return "practitioners";
    }

    @PostMapping("/practitioners")
    String register(@RequestParam String fullName, RedirectAttributes flash) {
        try {
            Practitioner registered = practitioners.register(fullName);
            flash.addFlashAttribute("message", "Profesional registrado. Cargá sus razones y su semana.");
            return "redirect:/practitioners/" + registered.getId();
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", "Revisá el nombre: es obligatorio y tiene hasta 150 caracteres.");
            return "redirect:/practitioners";
        }
    }

    @GetMapping("/practitioners/{id}")
    String detail(@PathVariable UUID id, Model model, RedirectAttributes flash) {
        Practitioner practitioner;
        try {
            practitioner = practitioners.byId(id);
        } catch (PractitionerNotFoundException e) {
            flash.addFlashAttribute("error", "No se encontró el profesional.");
            return "redirect:/practitioners";
        }
        List<WeekRow> week = new ArrayList<>(practitioner.getWorkingHours().stream().map(WeekRow::of).toList());
        for (int i = 0; i < EMPTY_ROWS; i++) {
            week.add(WeekRow.EMPTY);
        }
        LocalDateTime now = LocalDateTime.now(clock);

        model.addAttribute("practitioner", practitioner);
        model.addAttribute("week", week);
        model.addAttribute("days", Arrays.stream(DayOfWeek.values()).map(DayOption::of).toList());
        model.addAttribute("timeOff", practitioner.getTimeOff().stream()
                .filter(absence -> absence.endsAt().isAfter(now))
                .map(PractitionerViewController::describe)
                .toList());
        model.addAttribute("today", now.toLocalDate());
        return "practitioner";
    }

    @PostMapping("/practitioners/{id}/name")
    String rename(@PathVariable UUID id, @RequestParam String fullName, RedirectAttributes flash) {
        return attempt(id, () -> practitioners.rename(id, fullName), "Nombre guardado.",
                "Revisá el nombre: es obligatorio y tiene hasta 150 caracteres.", flash);
    }

    @PostMapping("/practitioners/{id}/reasons")
    String addReason(@PathVariable UUID id, @RequestParam String name, @RequestParam int durationMinutes,
            RedirectAttributes flash) {
        return attempt(id, () -> practitioners.addReason(id, name, durationMinutes), "Razón agregada.",
                REASON_RULES, flash);
    }

    @PostMapping("/practitioners/{id}/reasons/{reasonId}")
    String changeReason(@PathVariable UUID id, @PathVariable UUID reasonId, @RequestParam String name,
            @RequestParam int durationMinutes, RedirectAttributes flash) {
        return attempt(id, () -> practitioners.changeReason(id, reasonId, name, durationMinutes), "Razón guardada.",
                REASON_RULES, flash);
    }

    /**
     * The week is replaced whole, as the API does (D-28). A row left empty is dropped, which is
     * how a stretch is taken out; a row half filled is refused rather than guessed.
     */
    @PostMapping("/practitioners/{id}/weekly-schedule")
    String replaceWeek(@PathVariable UUID id,
            @RequestParam(name = "day", required = false) List<String> days,
            @RequestParam(name = "startsAt", required = false) List<String> starts,
            @RequestParam(name = "endsAt", required = false) List<String> ends,
            RedirectAttributes flash) {
        List<WorkingHours> hours = new ArrayList<>();
        int rows = days == null ? 0 : days.size();
        for (int i = 0; i < rows; i++) {
            String day = days.get(i);
            String start = i < starts.size() ? starts.get(i) : "";
            String end = i < ends.size() ? ends.get(i) : "";
            if (day.isBlank() && start.isBlank() && end.isBlank()) {
                continue;
            }
            if (day.isBlank() || start.isBlank() || end.isBlank()) {
                flash.addFlashAttribute("error", "Completá día, desde y hasta en cada fila, o dejala vacía para sacarla.");
                return "redirect:/practitioners/" + id;
            }
            try {
                hours.add(new WorkingHours(DayOfWeek.valueOf(day), LocalTime.parse(start), LocalTime.parse(end)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                flash.addFlashAttribute("error", "Cada tramo tiene que terminar después de empezar.");
                return "redirect:/practitioners/" + id;
            }
        }
        return attempt(id, () -> practitioners.replaceWeeklySchedule(id, hours), "Semana guardada.",
                "Cada tramo tiene que terminar después de empezar.", flash);
    }

    private static final String REASON_RULES =
            "Revisá la razón: el nombre es obligatorio (hasta 60 caracteres) y dura entre 1 y 480 minutos.";

    /**
     * Runs a change and comes back to the practitioner with a message. The refusals the rules
     * expect become Spanish; anything else is a real failure and is not hidden.
     */
    private String attempt(UUID id, Supplier<?> change, String success, String invalid, RedirectAttributes flash) {
        try {
            change.get();
            flash.addFlashAttribute("message", success);
        } catch (DuplicateReasonException e) {
            flash.addFlashAttribute("error", "Ya tiene una razón con ese nombre.");
        } catch (InvalidWeeklyScheduleException e) {
            flash.addFlashAttribute("error",
                    "Dos tramos del mismo día se superponen o se tocan. Sin pausa en el medio, cargalos como un solo tramo.");
        } catch (PractitionerNotFoundException | ReasonNotFoundException e) {
            flash.addFlashAttribute("error", "Eso ya no existe: recargá la pantalla.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", invalid);
        }
        return "redirect:/practitioners/" + id;
    }

    /** "vie 25/09/2026", "vie 25/09/2026 de 10:30 a 13:00", or "del lun 28/09/2026 al mié 30/09/2026". */
    private static String describe(TimeOff absence) {
        LocalDateTime from = absence.startsAt();
        LocalDateTime until = absence.endsAt();
        boolean wholeDays = from.toLocalTime().equals(LocalTime.MIDNIGHT) && until.toLocalTime().equals(LocalTime.MIDNIGHT);
        if (!wholeDays) {
            return DAY.format(from) + " de " + from.toLocalTime() + " a " + until.toLocalTime();
        }
        LocalDateTime lastDay = until.minusDays(1);
        return lastDay.toLocalDate().equals(from.toLocalDate())
                ? DAY.format(from)
                : "del " + DAY.format(from) + " al " + DAY.format(lastDay);
    }

    /** One row of the week's table; the empty one offers a new stretch. */
    record WeekRow(String day, LocalTime startsAt, LocalTime endsAt) {

        static final WeekRow EMPTY = new WeekRow("", null, null);

        static WeekRow of(WorkingHours hours) {
            return new WeekRow(hours.day().name(), hours.startsAt(), hours.endsAt());
        }
    }

    /** A day of the week as the select offers it: its value, and its name in Spanish. */
    record DayOption(String value, String label) {

        static DayOption of(DayOfWeek day) {
            String name = day.getDisplayName(TextStyle.FULL, SPANISH);
            return new DayOption(day.name(), name.substring(0, 1).toUpperCase(SPANISH) + name.substring(1));
        }
    }
}
