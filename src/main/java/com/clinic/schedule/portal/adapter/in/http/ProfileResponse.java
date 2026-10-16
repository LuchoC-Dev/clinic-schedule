package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;

import com.clinic.schedule.portal.domain.AgeBand;
import com.clinic.schedule.portal.domain.OwnData;

/**
 * A patient's data as they read it (D-81). {@code phone}, {@code email}, {@code insurance} and
 * {@code birthDate} are null when not given; an owner is null when the channel is the patient's own, and a
 * consent null when nobody agreed to be reminded there. {@code ageBand} is how old the family groups' rules count
 * them today, null without a date of birth: whether they register a child from the web (D-92, D-112).
 */
record ProfileResponse(String fullName, String document, LocalDate birthDate, AgeBand ageBand, Channel phone,
        Channel email, OwnData.Insurance insurance) {

    record Channel(String value, Owner owner, OwnData.Consent consent) {
    }

    record Owner(String name, String relation) {
    }

    static ProfileResponse of(OwnData data, LocalDate today) {
        return new ProfileResponse(data.fullName(), data.document(), data.birthDate(),
                data.birthDate() == null ? null : AgeBand.of(data.birthDate(), today), channel(data.phone()),
                channel(data.email()), data.insurance());
    }

    private static Channel channel(OwnData.Channel channel) {
        return channel == null ? null : new Channel(channel.value(),
                channel.ownerName() == null ? null : new Owner(channel.ownerName(), channel.ownerRelation()),
                channel.consent());
    }
}
