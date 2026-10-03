package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FamilyChangeRowRepository extends JpaRepository<FamilyChangeRow, UUID> {

    List<FamilyChangeRow> findByGroupIdOrderByChangedAtDesc(UUID groupId);
}
