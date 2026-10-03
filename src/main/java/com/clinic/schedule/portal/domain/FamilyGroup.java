package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A family group (D-84): a name reception gives it, and the patients in it. Formed by reception, at the desk;
 * a patient may be in several.
 *
 * <p>Changed by {@link FamilyGroupService} alone, which checks what needs the members' ages. It is immutable:
 * each change returns the group as it became, with the entry that records it.
 */
public final class FamilyGroup {

    /** As long as the column that stores it. */
    static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final String name;
    private final LocalDateTime createdAt;
    private final List<FamilyMember> members;

    private FamilyGroup(UUID id, String name, LocalDateTime createdAt, List<FamilyMember> members) {
        if (id == null || createdAt == null || members == null) {
            throw new IllegalArgumentException("A family group needs its id, when it was formed and its members");
        }
        this.id = id;
        this.name = name(name);
        this.createdAt = createdAt;
        this.members = List.copyOf(members);
        if (this.members.stream().map(FamilyMember::patientId).distinct().count() != this.members.size()) {
            throw new IllegalArgumentException("A patient is in a family group once");
        }
    }

    static FamilyUpdate create(String name, LocalDateTime at, String by) {
        FamilyGroup group = new FamilyGroup(UUID.randomUUID(), name, at, List.of());
        return new FamilyUpdate(group, FamilyChange.of(group.id, FamilyChange.Action.CREATED, null, at, by));
    }

    /** A group as it was stored. */
    public static FamilyGroup restore(UUID id, String name, LocalDateTime createdAt, List<FamilyMember> members) {
        return new FamilyGroup(id, name, createdAt, members);
    }

    FamilyUpdate with(FamilyMember member, FamilyChange.Action action, LocalDateTime at, String by) {
        List<FamilyMember> more = new ArrayList<>(members);
        more.add(member);
        return update(more, action, member.patientId(), at, by);
    }

    FamilyUpdate without(UUID patientId, LocalDateTime at, String by) {
        return update(members.stream().filter(member -> !member.patientId().equals(patientId)).toList(),
                FamilyChange.Action.REMOVED, patientId, at, by);
    }

    FamilyUpdate sharing(UUID patientId, boolean shares, LocalDateTime at, String by) {
        return update(members.stream()
                        .map(member -> member.patientId().equals(patientId) ? member.sharing(shares) : member)
                        .toList(),
                shares ? FamilyChange.Action.SHARING_STARTED : FamilyChange.Action.SHARING_STOPPED, patientId, at, by);
    }

    private FamilyUpdate update(List<FamilyMember> changed, FamilyChange.Action action, UUID patientId,
            LocalDateTime at, String by) {
        return new FamilyUpdate(new FamilyGroup(id, name, createdAt, changed),
                FamilyChange.of(id, action, patientId, at, by));
    }

    private static String name(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A family group's name has 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return stripped;
    }

    public Optional<FamilyMember> member(UUID patientId) {
        return members.stream().filter(member -> member.patientId().equals(patientId)).findFirst();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public List<FamilyMember> members() {
        return members;
    }
}
