package com.wallet.app.config;

import com.wallet.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminRoleBootstrapRunner implements ApplicationRunner {
    private final UserRepository userRepo;
    @Value("${paywallet.admin.email:}")
    private String adminEmail;

    public AdminRoleBootstrapRunner(UserRepository userRepo) { this.userRepo = userRepo; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) return;
        userRepo.findByEmail(adminEmail.trim().toLowerCase()).ifPresent(user -> {
            user.setRole("ADMIN");
            userRepo.save(user);
        });
    }
}
