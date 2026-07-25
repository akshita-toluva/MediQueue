package com.mediqueue.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name="queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @OneToOne
    @JoinColumn(name="appointment_id",nullable = false)
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name="doctor_id",nullable = false)
    private Doctor doctor;

    @Column(name = "estimated_wait_time")
    private int estimatedWaitTime;

    @Column(name = "actual_wait_time")
    private Integer actualWaitTime;

    @Column(nullable = false)
    private LocalDate date;

    @PrePersist
    public void prePersist()
    {
        if (this.date == null) {
            this.date = LocalDate.now();
        }
    }

}
