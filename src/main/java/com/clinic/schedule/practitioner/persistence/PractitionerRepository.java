package com.clinic.schedule.practitioner.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.practitioner.entity.Practitioner;

public interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {

    List<Practitioner> findAllByOrderByFullNameAsc();
}
