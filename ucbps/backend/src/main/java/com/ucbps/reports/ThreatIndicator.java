package com.ucbps.reports;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "threat_indicators")
public class ThreatIndicator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "number_hash", nullable = false, length = 64)
    public String numberHash;

    @Column(nullable = false, length = 2)
    public String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ThreatType type;

    @Column(name = "risk_score", nullable = false)
    public double riskScore;

    @Column(name = "time_bucket", nullable = false)
    public Instant timeBucket;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
