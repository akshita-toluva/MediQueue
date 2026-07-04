package com.mediqueue.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="appointments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name="patient_id",nullable = false)
    private User patient;

    @ManyToOne
    @JoinColumn(name="doctor_id",nullable = false)
    private Doctor doctor;

    @Column(nullable = false)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name="queue_position")
    private int queuePosition;

    @Column(name="booked_at")
    private LocalDateTime bookedAt;

    @PrePersist
    public void prePersist() {
        this.bookedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AppointmentStatus.PENDING;
        }
    }

}
