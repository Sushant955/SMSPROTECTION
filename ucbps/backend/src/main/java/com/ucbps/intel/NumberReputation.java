package com.ucbps.intel;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "number_reputation")
public class NumberReputation {
    @Id
    @Column(name = "number_hash", length = 64)
    public String numberHash;

    @Column(name = "report_count", nullable = false)
    public int reportCount;

    @Column(name = "distinct_reporters", nullable = false)
    public int distinctReporters;

    @Column(name = "avg_risk", nullable = false)
    public double avgRisk;

    @Column(nullable = false)
    public double score;

    @Column(nullable = false)
    public boolean flagged;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
}
