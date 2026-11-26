package com.mediqueue.repository;

import com.mediqueue.entity.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import com.mediqueue.dto.WaitSample;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QueueRepository extends JpaRepository<QueueEntry,Long> {
    Optional<QueueEntry> findByAppointmentId(Long appointmentId);
    List<QueueEntry> findByDoctorIdAndDate(Long doctorId, LocalDate date);
    List<QueueEntry> findTop5ByDoctorIdAndActualWaitTimeIsNotNullOrderByDateDesc(Long doctorId);
    List<QueueEntry> findByAppointmentIdIn(List<Long> appointmentIds);

    @Query("""
            SELECT new com.mediqueue.dto.WaitSample(a.department, q.initialEstimatedWait, q.actualWaitTime)
            FROM QueueEntry q JOIN q.appointment a
            WHERE a.status = com.mediqueue.entity.AppointmentStatus.COMPLETED
              AND q.initialEstimatedWait IS NOT NULL
              AND q.actualWaitTime IS NOT NULL
              AND a.bookedAt >= :since
            """)
    List<WaitSample> findWaitSamplesSince(@Param("since") LocalDateTime since);
}
