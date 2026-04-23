package com.muigo.wallet.controllers;

import com.muigo.wallet.dtos.WalletDtos.*;
import com.muigo.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "Wallet management and transaction endpoints")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new wallet")
    public ApiResponse<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        return ApiResponse.success("Wallet created successfully", walletService.createWallet(request));
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get wallet details and current balance")
    public ApiResponse<WalletResponse> getWallet(@PathVariable String walletId) {
        return ApiResponse.success(walletService.getWallet(walletId));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into a wallet")
    public ApiResponse<WalletResponse> deposit(@Valid @RequestBody DepositRequest request) {
        return ApiResponse.success("Deposit successful", walletService.deposit(request));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds from a wallet")
    public ApiResponse<WalletResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("Withdrawal successful", walletService.withdraw(request));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between wallets (atomic)")
    public ApiResponse<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ApiResponse.success("Transfer successful", walletService.transfer(request));
    }

    @GetMapping("/{walletId}/transactions")
    @Operation(summary = "Get paginated transaction history for a wallet")
    public ApiResponse<Page<TransactionResponse>> getTransactions(
            @PathVariable String walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransactionResponse> transactions =
                walletService.getTransactionHistory(walletId, PageRequest.of(page, size));
        return ApiResponse.success(transactions);
    }
}
