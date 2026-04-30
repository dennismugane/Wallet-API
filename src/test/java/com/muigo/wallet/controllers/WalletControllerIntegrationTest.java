package com.muigo.wallet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muigo.wallet.dtos.WalletDtos.*;
import com.muigo.wallet.models.Wallet;
import com.muigo.wallet.repositories.UserRepository;
import com.muigo.wallet.repositories.WalletRepository;
import com.muigo.wallet.service.AuthService;
import com.muigo.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("WalletController Integration Tests")
class WalletControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WalletService walletService;
    @Autowired AuthService authService;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;

    private String jwtToken;
    private WalletResponse testWallet;

    @BeforeEach
    void setUp() {
        // Register and login to get a JWT token for authenticated requests
        AuthResponse auth = authService.register(
                new RegisterRequest("test@muigo.com", "password123", "Test User"));
        jwtToken = auth.getToken();

        // Create a wallet with funds for testing
        testWallet = walletService.createWallet(new CreateWalletRequest("Test User"));
        walletService.deposit(new DepositRequest(testWallet.getId(), new BigDecimal("1000.00"), "seed"));
    }

    @Test
    @DisplayName("POST /api/v1/wallets — creates wallet and returns 201")
    void createWallet_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWalletRequest("Jane Doe"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ownerName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/wallets — returns 400 for blank owner name")
    void createWallet_returns400OnBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWalletRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/wallets/deposit — increases balance")
    void deposit_increasesBalance() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/deposit")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(testWallet.getId(), new BigDecimal("500.00"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1500.0));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/withdraw — returns 422 on insufficient funds")
    void withdraw_returns422OnInsufficientFunds() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/withdraw")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WithdrawRequest(testWallet.getId(), new BigDecimal("9999.00"), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Insufficient funds")));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions — returns paginated history")
    void getTransactions_returnsPaginatedHistory() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{id}/transactions", testWallet.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Unauthenticated request returns 403")
    void unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + testWallet.getId()))
                .andExpect(status().isForbidden());
    }
}
