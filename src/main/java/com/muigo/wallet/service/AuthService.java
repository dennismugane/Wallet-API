package com.muigo.wallet.service;

import com.muigo.wallet.dtos.WalletDtos.*;
import com.muigo.wallet.models.AppUser;
import com.muigo.wallet.repositories.UserRepository;
import com.muigo.wallet.security.JwtService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final MeterRegistry meterRegistry;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            meterRegistry.counter("wallet.auth.register.total", "status", "duplicate_email").increment();
            throw new IllegalArgumentException("Email already registered");
        }

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .build();

        AppUser saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        meterRegistry.counter("wallet.auth.register.total", "status", "success").increment();

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(saved.getId())
                .email(saved.getEmail())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            meterRegistry.counter("wallet.auth.login.total", "status", "failure").increment();
            throw ex;
        }

        AppUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();
        String token = jwtService.generateToken(user);

        meterRegistry.counter("wallet.auth.login.total", "status", "success").increment();

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }
}