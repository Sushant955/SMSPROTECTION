package com.ucbps.config;

import com.ucbps.auth.User;
import com.ucbps.auth.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first ADMIN account from ADMIN_EMAIL / ADMIN_PASSWORD env vars (registration can only create USER accounts). */
@Component
public class AdminSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder,
                       @Value("${ucbps.admin.email:}") String email,
                       @Value("${ucbps.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.length() < 8) return;
        String e = email.trim().toLowerCase();
        if (users.findByEmail(e).isPresent()) return;
        User u = new User();
        u.email = e;
        u.passwordHash = encoder.encode(password);
        u.role = "ADMIN";
        users.save(u);
    }
}
