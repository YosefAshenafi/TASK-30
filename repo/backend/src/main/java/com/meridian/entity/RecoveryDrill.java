package com.meridian.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recovery_drills")
public class RecoveryDrill {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "drill_date", nullable = false)
    private LocalDate drillDate;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @Column(name = "steps_completed", nullable = false)
    private int stepsCompleted = 0;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps = 8;

    @Column(nullable = false)
    private String outcome = "PASS";

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getDrillDate() { return drillDate; }
    public void setDrillDate(LocalDate drillDate) { this.drillDate = drillDate; }

    public UUID getPerformedBy() { return performedBy; }
    public void setPerformedBy(UUID performedBy) { this.performedBy = performedBy; }

    public int getStepsCompleted() { return stepsCompleted; }
    public void setStepsCompleted(int stepsCompleted) { this.stepsCompleted = stepsCompleted; }

    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
