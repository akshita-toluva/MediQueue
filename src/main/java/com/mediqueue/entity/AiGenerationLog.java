package com.mediqueue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Every AI call, success or failure: exactly what was sent, what came back, which model, how long.
@Entity
@Table(name = "ai_generation_log",
        indexes = @Index(name = "idx_ai_log_summary", columnList = "summary_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiGenerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private DischargeSummary summary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "prompt_input", nullable = false, length = 4000)
    private String promptInput;

    @Column(length = 4000)
    private String output;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiGenerationStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}