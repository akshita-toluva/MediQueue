package com.mediqueue.mediqueue.repository;

import com.mediqueue.mediqueue.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor,Long> {
    List<Doctor> findByAvailableTrue();
    List<Doctor> findByDepartment(String department);
    List<Doctor> findByDepartmentAndAvailableTrue(String department);
}
