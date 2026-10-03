package com.clinic.schedule.portal.adapter.in.screen;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import com.clinic.schedule.portal.application.usecase.FindFamilyGroupUseCase;
import com.clinic.schedule.portal.application.usecase.ManageFamilyGroupUseCase;
import com.clinic.schedule.portal.domain.AgeBand;
import com.clinic.schedule.portal.domain.FamilyChange;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyGroupService;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.FamilyPerson;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;

/**
 * Reception's screens of the family groups (D-84), in Spanish: the groups, and each one's members — who shares
 * their appointments, who asked for a child to be added — with the forms to form it at the desk and its
 * history. Every action answers with a redirect back, saying what happened (D-33).
 */
@Controller
class FamilyGroupViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    private final ManageFamilyGroupUseCase manage;
    private final FindFamilyGroupUseCase find;
    private final PatientReader patients;
    private final Clock clock;

    FamilyGroupViewController(ManageFamilyGroupUseCase manage, FindFamilyGroupUseCase find, PatientReader patients,
            Clock clock) {
        this.manage = manage;
        this.find = find;
        this.patients = patients;
        this.clock = clock;
    }

    /** Every group, or the patient's when one is given. */
    @GetMapping("/family-groups")
    String groups(@RequestParam(required = false) UUID patient, Model model) {
        model.addAttribute("groups", find.list(patient));
        model.addAttribute("patient", patient == null ? null
                : patients.findAll(List.of(patient)).stream().findFirst().orElse(null));
        return "family-groups";
    }

    @PostMapping("/family-groups")
    String create(@RequestParam String name, RedirectAttributes redirect) {
        try {
            FamilyGroup group = manage.create(name);
            redirect.addFlashAttribute("message", "Grupo creado. Agregá a sus miembros.");
            return "redirect:/family-groups/" + group.id();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "El grupo necesita un nombre de hasta 100 caracteres.");
            return "redirect:/family-groups";
        }
    }

    /** The group, with a search of patients to add when {@code query} is given. */
    @GetMapping("/family-groups/{id}")
    String group(@PathVariable UUID id, @RequestParam(required = false) String query, Model model) {
        FamilyGroup group;
        try {
            group = find.byId(id);
        } catch (FamilyGroupNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        LocalDate today = LocalDate.now(clock);
        List<FamilyChange> history = find.history(id);
        // Whoever the history names, members who left included.
        Map<UUID, PatientSummary> byId = patients.findAll(history.stream().map(FamilyChange::patientId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        List<MemberRow> members = group.members().stream()
                .map(member -> MemberRow.of(member, byId, today))
                .toList();
        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("adults", members.stream().filter(MemberRow::adult).toList());
        model.addAttribute("query", query);
        model.addAttribute("found", query == null || query.isBlank() ? List.of() : patients.search(query));
        model.addAttribute("history", history.stream().map(change -> HistoryRow.of(change, byId)).toList());
        return "family-group";
    }

    @PostMapping("/family-groups/{id}/members")
    String join(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(defaultValue = "false") boolean shares, RedirectAttributes redirect) {
        return attempt(id, () -> manage.join(id, patientId, shares), "Entró al grupo.", redirect);
    }

    @PostMapping("/family-groups/{id}/children")
    String addChild(@PathVariable UUID id, @RequestParam UUID patientId, @RequestParam UUID requestedBy,
            @RequestParam FamilyMember.Relation relation, @RequestParam(defaultValue = "false") boolean proofSeen,
            RedirectAttributes redirect) {
        return attempt(id, () -> manage.addChild(id, patientId, requestedBy, relation, proofSeen),
                "Menor agregado al grupo.", redirect);
    }

    @PostMapping("/family-groups/{id}/members/{patientId}/remove")
    String remove(@PathVariable UUID id, @PathVariable UUID patientId, RedirectAttributes redirect) {
        return attempt(id, () -> manage.remove(id, patientId), "Salió del grupo.", redirect);
    }

    @PostMapping("/family-groups/{id}/members/{patientId}/sharing")
    String share(@PathVariable UUID id, @PathVariable UUID patientId, @RequestParam boolean shares,
            RedirectAttributes redirect) {
        return attempt(id, () -> manage.share(id, patientId, shares),
                shares ? "Ahora comparte sus turnos con el grupo." : "Sus turnos ya no se comparten con el grupo.",
                redirect);
    }

    private String attempt(UUID id, Runnable action, String done, RedirectAttributes redirect) {
        try {
            action.run();
            redirect.addFlashAttribute("message", done);
        } catch (FamilyRefusedException e) {
            redirect.addFlashAttribute("error", refusal(e));
        } catch (FamilyGroupNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return "redirect:/family-groups/" + id;
    }

    /** What reception reads when the group's rules refuse it. */
    private static String refusal(FamilyRefusedException e) {
        return switch (e.reason()) {
            case NO_BIRTH_DATE -> "Para entrar a un grupo, el paciente necesita su fecha de nacimiento cargada.";
            case A_CHILD -> "Es menor de 13: se agrega como menor, a pedido de su madre, padre o tutor del grupo.";
            case NOT_A_CHILD -> "Tiene 13 años o más: entra por sí mismo, presente y aceptando.";
            case ALREADY_A_MEMBER -> "Ya está en el grupo.";
            case NOT_A_MEMBER -> "No está en el grupo.";
            case NOT_AN_ADULT_MEMBER -> "Un menor se agrega a pedido de un adulto del grupo.";
            case NO_PROOF -> "Para agregar un menor, mirá la documentación que acredita el vínculo y marcalo.";
            case CHILDREN_SHARE -> "Un menor de 13 comparte siempre sus turnos con su familia.";
        };
    }

    private static String relation(FamilyMember.Relation relation) {
        return switch (relation) {
            case MOTHER -> "madre";
            case FATHER -> "padre";
            case GUARDIAN -> "tutor o tutora";
        };
    }

    /** A member as the screen lists them, in Spanish. */
    record MemberRow(UUID patientId, String name, String age, String sharing, String requestedBy, boolean adult,
            boolean decides, boolean shares) {

        static MemberRow of(FamilyMember member, Map<UUID, PatientSummary> patients, LocalDate today) {
            PatientSummary patient = patients.get(member.patientId());
            AgeBand band = patient.birthDate() == null ? null : AgeBand.of(patient.birthDate(), today);
            boolean shares = FamilyGroupService.sharesOn(member, new FamilyPerson(member.patientId(),
                    patient.birthDate()), today);
            return new MemberRow(member.patientId(), patient.fullName(),
                    band == null ? "sin fecha de nacimiento" : switch (band) {
                        case CHILD -> "menor de 13";
                        case ADOLESCENT -> "de 13 a 17";
                        case ADULT -> "adulto";
                    },
                    band == AgeBand.CHILD ? "Siempre (menor de 13)"
                            : shares ? "Comparte sus turnos" : "Turnos privados",
                    member.requestedBy() == null ? null : "A pedido de "
                            + patients.get(member.requestedBy()).fullName() + " (" + relation(member.relation()) + ")",
                    band == AgeBand.ADULT, band != null && band != AgeBand.CHILD, shares);
        }
    }

    /** An entry of the group's history, in Spanish. */
    record HistoryRow(String when, String what, String by) {

        static HistoryRow of(FamilyChange change, Map<UUID, PatientSummary> patients) {
            String who = change.patientId() == null ? "" : " — " + patients.get(change.patientId()).fullName();
            return new HistoryRow(WHEN.format(change.at()), switch (change.action()) {
                case CREATED -> "Grupo creado";
                case JOINED -> "Entró";
                case CHILD_ADDED -> "Agregado como menor";
                case REMOVED -> "Salió";
                case SHARING_STARTED -> "Empezó a compartir sus turnos";
                case SHARING_STOPPED -> "Dejó de compartir sus turnos";
            } + who, change.by());
        }
    }
}
