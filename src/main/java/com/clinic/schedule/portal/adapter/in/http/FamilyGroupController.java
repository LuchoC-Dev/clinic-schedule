package com.clinic.schedule.portal.adapter.in.http;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.portal.application.usecase.FindFamilyGroupUseCase;
import com.clinic.schedule.portal.application.usecase.ManageFamilyGroupUseCase;
import com.clinic.schedule.portal.domain.FamilyChange;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyMember;

import jakarta.validation.Valid;

/**
 * Reception's side of the family groups (D-84): forming them at the desk and reading their history. Reception's
 * alone, which the route decides (D-34). Business operations are POSTs on a sub-path (D-23).
 */
@RestController
class FamilyGroupController {

    private final ManageFamilyGroupUseCase manage;
    private final FindFamilyGroupUseCase find;
    private final PatientReader patients;
    private final Clock clock;

    FamilyGroupController(ManageFamilyGroupUseCase manage, FindFamilyGroupUseCase find, PatientReader patients,
            Clock clock) {
        this.manage = manage;
        this.find = find;
        this.patients = patients;
        this.clock = clock;
    }

    @PostMapping("/api/family-groups")
    ResponseEntity<FamilyGroupResponse> create(@Valid @RequestBody FamilyGroupRequests.Create request) {
        FamilyGroup group = manage.create(request.name());
        return ResponseEntity.created(URI.create("/api/family-groups/" + group.id())).body(respond(group));
    }

    /** Every group, by name, or the patient's. */
    @GetMapping("/api/family-groups")
    List<FamilyGroupResponse> list(@RequestParam(required = false) UUID patientId) {
        return find.list(patientId).stream().map(this::respond).toList();
    }

    @GetMapping("/api/family-groups/{id}")
    FamilyGroupResponse get(@PathVariable UUID id) {
        return respond(find.byId(id));
    }

    @PostMapping("/api/family-groups/{id}/members")
    FamilyGroupResponse join(@PathVariable UUID id, @Valid @RequestBody FamilyGroupRequests.Join request) {
        return respond(manage.join(id, request.patientId(), request.shares()));
    }

    @PostMapping("/api/family-groups/{id}/children")
    FamilyGroupResponse addChild(@PathVariable UUID id, @Valid @RequestBody FamilyGroupRequests.AddChild request) {
        return respond(manage.addChild(id, request.patientId(), request.requestedBy(), request.relation(),
                request.proofSeen()));
    }

    /** Reception saw what proves the relation of a child an adult registered on the web (D-92). */
    @PostMapping("/api/family-groups/{id}/members/{patientId}/relation-proof")
    FamilyGroupResponse proveRelation(@PathVariable UUID id, @PathVariable UUID patientId,
            @RequestBody FamilyGroupRequests.RelationProof request) {
        return respond(manage.proveRelation(id, patientId, request.proofSeen()));
    }

    @PostMapping("/api/family-groups/{id}/members/{patientId}/remove")
    FamilyGroupResponse remove(@PathVariable UUID id, @PathVariable UUID patientId) {
        return respond(manage.remove(id, patientId));
    }

    @PostMapping("/api/family-groups/{id}/members/{patientId}/sharing")
    FamilyGroupResponse share(@PathVariable UUID id, @PathVariable UUID patientId,
            @RequestBody FamilyGroupRequests.Share request) {
        return respond(manage.share(id, patientId, request.shares()));
    }

    /** What happened to the group, the most recent first. */
    @GetMapping("/api/family-groups/{id}/changes")
    List<FamilyChangeResponse> changes(@PathVariable UUID id) {
        List<FamilyChange> history = find.history(id);
        Map<UUID, PatientSummary> named = patients.findAll(history.stream().map(FamilyChange::patientId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        return history.stream().map(change -> FamilyChangeResponse.of(change, named)).toList();
    }

    private FamilyGroupResponse respond(FamilyGroup group) {
        Map<UUID, PatientSummary> byId = patients.findAll(group.members().stream().map(FamilyMember::patientId)
                        .toList()).stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        return FamilyGroupResponse.of(group, byId, LocalDate.now(clock));
    }
}
