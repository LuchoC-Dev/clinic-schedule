package com.clinic.schedule.practitioner;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {

    List<Practitioner> findAllByOrderByFullNameAsc();
}
