package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AddCreditRequest {
    @NotNull @Positive
    private BigDecimal amount;
    private String reason;
    private LocalDateTime expirationDate;
}
