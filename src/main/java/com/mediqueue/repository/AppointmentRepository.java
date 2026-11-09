package com.mediqueue.repository;

import com.mediqueue.dto.DepartmentCount;
import com.mediqueue.dto.HourlyCount;
import com.mediqueue.dto.NoShowRateResponse;
import com.mediqueue.entity.Appointment;
import com.mediqueue.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment,Long> {
    List<Appointment> findByPatientId(Long patient_id);
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, AppointmentStatus status);
    int countByDoctorIdAndStatus(Long doctorId,AppointmentStatus status);
    List<Appointment> findByStatusInAndBookedAtAfter(List<AppointmentStatus> statuses, LocalDateTime cutoff);
    List<Appointment> findByPatientIdOrderByBookedAtDesc(Long patientId);
    Optional<Appointment> findFirstByPatientIdAndDoctorIdAndStatus(Long patientId, Long doctorId, AppointmentStatus status);
    List<Appointment> findByStatusAndBookedAtBefore(AppointmentStatus status, LocalDateTime cutoff);

    //Admin analytics
    // Live load: appointments still waiting or being seen, booked since :since, one row per department.
    // "SELECT new ..." makes Hibernate build the record directly, with no Object[] casting.
    @Query("""
            SELECT new com.mediqueue.dto.DepartmentCount(a.department, COUNT(a))
            FROM Appointment a
            WHERE a.status IN :statuses AND a.bookedAt >= :since
            GROUP BY a.department
            """)
    List<DepartmentCount> countActiveByDepartment(@Param("statuses") List<AppointmentStatus> statuses,
                                                  @Param("since") LocalDateTime since);

    // Peak hours: bookings per hour of the day
    // Hours with zero bookings return NO row; the service fills those gaps with 0.
    @Query("""
            SELECT new com.mediqueue.dto.HourlyCount(EXTRACT(HOUR FROM a.bookedAt), COUNT(a))
            FROM Appointment a
            WHERE a.bookedAt >= :since
            GROUP BY EXTRACT(HOUR FROM a.bookedAt)
            """)
    List<HourlyCount> countBookingsByHour(@Param("since") LocalDateTime since);

    // No-show rate per department.
    // Denominator = resolved appointments only (COMPLETED + NO_SHOW), the same definition
    // SlidingWindowNoShowTracker uses, so the admin view and the wait-time engine never disagree.
    @Query("""
            SELECT new com.mediqueue.dto.NoShowRateResponse(
                a.department,
                SUM(CASE WHEN a.status = com.mediqueue.entity.AppointmentStatus.NO_SHOW THEN 1 ELSE 0 END),
                COUNT(a))
            FROM Appointment a
            WHERE a.status IN (com.mediqueue.entity.AppointmentStatus.COMPLETED,
                               com.mediqueue.entity.AppointmentStatus.NO_SHOW)
              AND a.bookedAt >= :since
            GROUP BY a.department
            ORDER BY a.department
            """)
    List<NoShowRateResponse> noShowRatesByDepartment(@Param("since") LocalDateTime since);

}
