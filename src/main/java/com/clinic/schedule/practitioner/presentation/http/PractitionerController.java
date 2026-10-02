package com.clinic.schedule.practitioner.presentation.http;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinic.schedule.practitioner.entity.Practitioner;
import com.clinic.schedule.practitioner.entity.Reason;
import com.clinic.schedule.practitioner.service.PractitionerService;

/**
 * Reception's configuration of each practitioner: who they are, the reasons an appointment
 * can be requested for, and their usual week.
 *
 * <p>Reasons are added and changed one at a time, because each has an identity worth keeping
 * across a rename. The week is replaced whole, because it is edited as a table. There is no
 * delete for either a practitioner or a reason: nothing in v1 asks for it, and both are
 * referenced by past appointments.
 */
@RestController
@RequestMapping("/api/practitioners")
class PractitionerController {

    private final PractitionerService practitioners;

    PractitionerController(PractitionerService practitioners) {
        this.practitioners = practitioners;
    }

    @PostMapping
    ResponseEntity<PractitionerResponse> register(@Valid @RequestBody PractitionerRequest request) {
        Practitioner registered = practitioners.register(request.fullName());
        return ResponseEntity
                .created(URI.create("/api/practitioners/" + registered.getId()))
                .body(PractitionerResponse.of(registered));
    }

    @PutMapping("/{id}")
    PractitionerResponse rename(@PathVariable UUID id, @Valid @RequestBody PractitionerRequest request) {
        return PractitionerResponse.of(practitioners.rename(id, request.fullName()));
    }

    @GetMapping("/{id}")
    PractitionerResponse byId(@PathVariable UUID id) {
        return PractitionerResponse.of(practitioners.byId(id));
    }

    @GetMapping
    List<PractitionerResponse> all() {
        return practitioners.all().stream().map(PractitionerResponse::of).toList();
    }

    /** 201 without a Location: a reason is read as part of its practitioner, not on its own. */
    @PostMapping("/{id}/reasons")
    ResponseEntity<ReasonResponse> addReason(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        Reason added = practitioners.addReason(id, request.name(), request.durationMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReasonResponse.of(added));
    }

    @PutMapping("/{id}/reasons/{reasonId}")
    ReasonResponse changeReason(@PathVariable UUID id, @PathVariable UUID reasonId,
            @Valid @RequestBody ReasonRequest request) {
        return ReasonResponse.of(practitioners.changeReason(id, reasonId, request.name(), request.durationMinutes(),
                request.webBookable()));
    }

    @PutMapping("/{id}/weekly-schedule")
    PractitionerResponse replaceWeeklySchedule(@PathVariable UUID id,
            @Valid @RequestBody WeeklyScheduleRequest request) {
        return PractitionerResponse.of(practitioners.replaceWeeklySchedule(id, request.toWorkingHours()));
    }
}
