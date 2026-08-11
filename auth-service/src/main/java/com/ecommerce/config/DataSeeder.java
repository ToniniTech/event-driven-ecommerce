package com.ecommerce.config;

import com.ecommerce.domain.Role;
import com.ecommerce.domain.User;
import com.ecommerce.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor

public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${admin.email:admin@ecommerce.local}")
    private String adminEmail;

    @Value("${admin.password:neymarsantos123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.existsByEmail(adminEmail)){
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .firstName("Neymar")
                .lastName("Santos")
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

    }

}
