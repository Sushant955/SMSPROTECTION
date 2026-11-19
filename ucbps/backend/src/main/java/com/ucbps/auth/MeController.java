package com.ucbps.auth;

import com.ucbps.intel.ThreatService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    public record ConsentRequest(boolean granted) {}

    private final UserRepository users;
    private final ThreatService threats;

    public MeController(UserRepository users, ThreatService threats) {
        this.users = users;
        this.threats = threats;
    }

    @GetMapping
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser me) {
        User u = users.findById(me.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("email", u.email, "role", u.role, "reportingConsent", u.reportingConsent);
    }

    @PutMapping("/consent")
    public Map<String, Object> consent(@AuthenticationPrincipal AuthUser me, @RequestBody ConsentRequest r) {
        User u = users.findById(me.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        u.reportingConsent = r.granted();
        users.save(u);
        return Map.of("reportingConsent", u.reportingConsent);
    }

    /** Right to erasure: deletes the account and every indicator it ever reported. */
    @DeleteMapping("/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@AuthenticationPrincipal AuthUser me) {
        threats.eraseUser(me.id());
    }
}
