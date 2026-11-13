package com.mediqueue.repository;

import com.mediqueue.entity.PatientHealthProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientHealthProfileRepository extends JpaRepository<PatientHealthProfile, Long> {
    Optional<PatientHealthProfile> findByPatientId(Long patientId);
}