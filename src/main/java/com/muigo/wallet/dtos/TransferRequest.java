package com.muigo.wallet.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data               // Generates Getters, Setters, toString, equals, and hashCode
@NoArgsConstructor  // Required by JPA/Hibernate
@AllArgsConstructor // Useful for creating objects easily
public class TransferRequest {
    private String fromWallet;
    private String toWallet;
    private double amount;
}
