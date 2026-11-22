package com.mediqueue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// @Getter/@Setter, NOT @Data: @Data's toString/hashCode would walk LAZY relations
// (appointment -> patient -> ...) and fire extra queries or fail outside a transaction.
@Entity
@Table(name = "discharge_summary")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DischargeSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true: ONE summary per visit, enforced by the database, not just by code
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "diagnosis_notes", nullable = false, length = 4000)
    private String diagnosisNotes;

    @Column(name = "follow_up_instructions", length = 2000)
    private String followUpInstructions;

    // Day 26: the AI suggestion currently attached. Its text lives in the log row (one source of truth).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diet_suggestion_log_id")
    private AiGenerationLog dietSuggestionLog;

    // Set only when the doctor approves THAT suggestion; cleared on every regeneration
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diet_approved_by")
    private User dietApprovedBy;

    @Column(name = "diet_approved_at")
    private LocalDateTime dietApprovedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}