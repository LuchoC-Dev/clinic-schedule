package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FamilyGroupRowRepository extends JpaRepository<FamilyGroupRow, UUID> {

    List<FamilyGroupRow> findAllByOrderByNameAsc();

    @Query("select distinct g from FamilyGroupRow g join g.members m where m.patientId = :patientId order by g.name")
    List<FamilyGroupRow> findOf(UUID patientId);
}
