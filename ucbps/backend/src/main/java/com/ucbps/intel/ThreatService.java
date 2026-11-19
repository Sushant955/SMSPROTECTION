package com.ucbps.intel;

import com.ucbps.auth.UserRepository;
import com.ucbps.reports.IndicatorRepository;
import com.ucbps.reports.ThreatIndicator;
import com.ucbps.reports.ThreatType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ThreatService {

    /** A number is only flagged when several independent users report it. One noisy user can never blacklist a number. */
    public static final int MIN_REPORTERS = 3;
    public static final double FLAG_SCORE = 0.6;

    private final IndicatorRepository indicators;
    private final ReputationRepository reputations;
    private final UserRepository users;

    public ThreatService(IndicatorRepository indicators, ReputationRepository reputations, UserRepository users) {
        this.indicators = indicators;
        this.reputations = reputations;
        this.users = users;
    }

    public static double score(double avgRisk, long distinctReporters) {
        return Math.min(1.0, 0.6 * avgRisk + 0.4 * Math.min(1.0, distinctReporters / 5.0));
    }

    public static boolean isFlagged(double score, long distinctReporters) {
        return distinctReporters >= MIN_REPORTERS && score >= FLAG_SCORE;
    }

    @Transactional
    public NumberReputation record(Long userId, String hash, String country, ThreatType type, double risk) {
        ThreatIndicator t = new ThreatIndicator();
        t.userId = userId;
        t.numberHash = hash;
        t.country = country;
        t.type = type;
        t.riskScore = risk;
        t.timeBucket = Instant.now().truncatedTo(ChronoUnit.HOURS);
        indicators.save(t);
        indicators.flush();
        return recompute(hash);
    }

    @Transactional
    public NumberReputation recompute(String hash) {
        long count = indicators.countByNumberHash(hash);
        if (count == 0) {
            reputations.findById(hash).ifPresent(reputations::delete);
            return null;
        }
        long distinct = indicators.countDistinctReporters(hash);
        double avg = indicators.avgRisk(hash);
        NumberReputation r = reputations.findById(hash).orElseGet(NumberReputation::new);
        r.numberHash = hash;
        r.reportCount = (int) count;
        r.distinctReporters = (int) distinct;
        r.avgRisk = avg;
        r.score = score(avg, distinct);
        r.flagged = isFlagged(r.score, distinct);
        r.updatedAt = Instant.now();
        return reputations.save(r);
    }

    @Transactional
    public void eraseUser(Long userId) {
        List<String> hashes = indicators.hashesByUser(userId);
        indicators.deleteByUserId(userId);
        indicators.flush();
        users.deleteById(userId);
        hashes.forEach(this::recompute);
    }
}
