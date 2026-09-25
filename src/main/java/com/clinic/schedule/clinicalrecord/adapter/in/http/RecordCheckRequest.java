package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.clinicalrecord.domain.PatientId;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of a check done without a consultation. {@code id} is optional and makes a retry safe
 * (D-19); without a {@code result}, the result is pending (D-53). With an {@code orderId} it fulfils
 * that order, and the description may be left out: it is what the order asks for (D-55).
 */
record RecordCheckRequest(
        UUID id,

        @NotNull(message = "the patient is required")
        UUID patientId,

        @Size(max = Check.MAX_DESCRIPTION_LENGTH, message = "the description is at most 2000 characters")
        String description,

        @Size(max = CheckResult.MAX_TEXT_LENGTH, message = "the result is at most 10000 characters")
        String result,

        UUID orderId) {

    RecordCheckUseCase.Command toCommand() {
        return new RecordCheckUseCase.Command(id == null ? CheckId.newId() : new CheckId(id),
                new PatientId(patientId), description, result, orderId == null ? null : new CheckOrderId(orderId));
    }
}
