package com.mediqueue.repository;

import com.mediqueue.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.mediqueue.dto.DoctorCountByDepartment;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor,Long> {
    List<Doctor> findByAvailableTrue();
    List<Doctor> findByDepartment(String department);
    List<Doctor> findByDepartmentAndAvailableTrue(String department);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
    Optional<Doctor> findByUserId(Long userId);

    // Admin analytics :  total and available doctors per department in ONE query.
    // SUM(CASE ...) counts only the rows where available = true.
    @Query("""
            SELECT new com.mediqueue.dto.DoctorCountByDepartment(
                d.department, COUNT(d), SUM(CASE WHEN d.available = true THEN 1 ELSE 0 END))
            FROM Doctor d
            GROUP BY d.department
            """)
    List<DoctorCountByDepartment> countDoctorsByDepartment();
}
