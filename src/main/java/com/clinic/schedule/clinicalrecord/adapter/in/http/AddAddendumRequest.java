package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.AddAddendumUseCase;
import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of an addendum. {@code id} is optional and makes a retry safe (D-19); the author and
 * the time are whoever sends it, and now (D-52).
 */
record AddAddendumRequest(
        UUID id,

        @NotBlank(message = "the text is required")
        @Size(max = Addendum.MAX_TEXT_LENGTH, message = "the text is at most 2000 characters")
        String text) {

    AddAddendumUseCase.Command toCommand(UUID consultationId) {
        return new AddAddendumUseCase.Command(new ConsultationId(consultationId),
                id == null ? AddendumId.newId() : new AddendumId(id), text);
    }
}
