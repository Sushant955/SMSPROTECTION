package com.ucbps.intel;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/intel")
public class IntelController {
    private final ReputationRepository reputations;

    public IntelController(ReputationRepository reputations) {
        this.reputations = reputations;
    }

    @GetMapping("/reputation/{hash}")
    public Map<String, Object> reputation(@PathVariable String hash) {
        if (!hash.matches("^[a-f0-9]{64}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hash");
        }
        return reputations.findById(hash).map(IntelController::view).orElseGet(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numberHash", hash);
            m.put("reportCount", 0);
            m.put("distinctReporters", 0);
            m.put("score", 0.0);
            m.put("flagged", false);
            return m;
        });
    }

    /** Delta feed of flagged hashes for the offline blocklist on devices. */
    @GetMapping("/feed")
    public List<Map<String, Object>> feed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        Instant s = since == null ? Instant.EPOCH : since;
        return reputations.findByFlaggedTrueAndUpdatedAtAfter(s).stream().map(IntelController::view).toList();
    }

    private static Map<String, Object> view(NumberReputation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numberHash", r.numberHash);
        m.put("reportCount", r.reportCount);
        m.put("distinctReporters", r.distinctReporters);
        m.put("score", r.score);
        m.put("flagged", r.flagged);
        return m;
    }
}
