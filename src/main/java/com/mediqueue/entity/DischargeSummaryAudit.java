package com.mediqueue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Append-only log: who did what to which summary, when. On UPDATED it keeps the OLD text,
// so a correction never silently erases what the patient may already have read.
@Entity
@Table(name = "discharge_summary_audit",
        indexes = @Index(name = "idx_discharge_audit_summary", columnList = "summary_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DischargeSummaryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private DischargeSummary summary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DischargeAuditAction action;

    @Column(name = "previous_diagnosis_notes", length = 4000)
    private String previousDiagnosisNotes;

    @Column(name = "previous_follow_up", length = 2000)
    private String previousFollowUp;

    // Which AI suggestion the patient actually saw in this view (null = none shown)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shown_diet_log_id")
    private AiGenerationLog shownDietLog;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}