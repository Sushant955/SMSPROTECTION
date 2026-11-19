package com.ucbps.admin;

import com.ucbps.auth.UserRepository;
import com.ucbps.intel.ReputationRepository;
import com.ucbps.reports.IndicatorRepository;
import com.ucbps.reports.ThreatIndicator;
import com.ucbps.reports.ThreatType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final UserRepository users;
    private final IndicatorRepository indicators;
    private final ReputationRepository reputations;

    public AdminController(UserRepository users, IndicatorRepository indicators, ReputationRepository reputations) {
        this.users = users;
        this.indicators = indicators;
        this.reputations = reputations;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ThreatType t : ThreatType.values()) byType.put(t.name(), 0L);
        for (Object[] row : indicators.countByType()) byType.put(((ThreatType) row[0]).name(), (Long) row[1]);

        List<Map<String, Object>> byCountry = indicators.countByCountry().stream()
                .map(row -> Map.<String, Object>of("country", row[0], "count", row[1])).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("protectedDevices", users.countByRole("USER"));
        out.put("totalEvents", indicators.count());
        out.put("flaggedNumbers", reputations.countByFlaggedTrue());
        out.put("smsEvents", byType.get("SMS_FLOOD") + byType.get("OTP_FLOOD") + byType.get("SPAM_SMS"));
        out.put("callEvents", byType.get("CALL_FLOOD"));
        out.put("byType", byType);
        out.put("byCountry", byCountry);
        return out;
    }

    @GetMapping("/incidents")
    public List<Map<String, Object>> incidents() {
        return indicators.findTop20ByOrderByCreatedAtDesc().stream().map(AdminController::row).toList();
    }

    private static Map<String, Object> row(ThreatIndicator t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("type", t.type.name());
        m.put("country", t.country);
        m.put("riskScore", t.riskScore);
        m.put("numberHashPrefix", t.numberHash.substring(0, 8));
        m.put("createdAt", t.createdAt);
        return m;
    }
}
