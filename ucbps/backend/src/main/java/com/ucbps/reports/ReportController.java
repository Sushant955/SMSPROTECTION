package com.ucbps.reports;

import com.ucbps.auth.AuthUser;
import com.ucbps.auth.User;
import com.ucbps.auth.UserRepository;
import com.ucbps.common.CounterStore;
import com.ucbps.intel.NumberReputation;
import com.ucbps.intel.ThreatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    /** The only data a device ever uploads: a keyed hash of the number, a country, a type and a score. No message text, no contacts. */
    public record ReportRequest(
            @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String numberHash,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country,
            @NotNull ThreatType type,
            double riskScore) {}

    private static final int MAX_REPORTS_PER_HOUR = 120;

    private final UserRepository users;
    private final ThreatService threats;
    private final CounterStore counters;

    public ReportController(UserRepository users, ThreatService threats, CounterStore counters) {
        this.users = users;
        this.threats = threats;
        this.counters = counters;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> report(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ReportRequest r) {
        User u = users.findById(me.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!u.reportingConsent) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Threat reporting consent not granted");
        }
        if (!(r.riskScore() >= 0.0 && r.riskScore() <= 1.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "riskScore must be between 0 and 1");
        }
        if (counters.increment("report:" + me.id(), Duration.ofHours(1)) > MAX_REPORTS_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Report rate limit exceeded");
        }
        NumberReputation rep = threats.record(me.id(), r.numberHash(), r.country(), r.type(), r.riskScore());
        return Map.of("flagged", rep.flagged, "score", rep.score);
    }
}
