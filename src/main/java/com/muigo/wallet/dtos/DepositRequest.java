package com.muigo.wallet.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data               // Generates Getters, Setters, toString, equals, and hashCode
@NoArgsConstructor  // Required by JPA/Hibernate
@AllArgsConstructor // Useful for creating objects easily
public class DepositRequest {
    private String walletId;
    private double amount;
}
