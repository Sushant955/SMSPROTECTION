package com.ucbps.auth;

import com.ucbps.config.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Pattern(regexp = "^[A-Z]{2}$") String country,
            boolean reportingConsent) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record TokenResponse(String token, String role) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User u = new User();
        u.email = email;
        u.passwordHash = encoder.encode(r.password());
        u.role = "USER"; // admins are only created by AdminSeeder
        u.country = r.country();
        u.reportingConsent = r.reportingConsent();
        users.save(u);
        return new TokenResponse(jwt.issue(u.id, u.email, u.role), u.role);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase()).orElse(null);
        if (u == null || !encoder.matches(r.password(), u.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new TokenResponse(jwt.issue(u.id, u.email, u.role), u.role);
    }
}
