package com.rowingclub.backend.dto;

import com.rowingclub.backend.entity.FinancialLedger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerDto {
    private Long id;
    private Long userId;
    private String userFullName;
    private BigDecimal amount;
    private String reason;
    private BigDecimal runningBalance;
    private LocalDateTime timestamp;
    private LocalDateTime expirationDate;

    public static LedgerDto from(FinancialLedger entry) {
        return LedgerDto.builder()
                .id(entry.getId())
                .userId(entry.getUser().getId())
                .userFullName(entry.getUser().getFullName())
                .amount(entry.getAmount())
                .reason(entry.getReason())
                .runningBalance(entry.getRunningBalance())
                .timestamp(entry.getTimestamp())
                .expirationDate(entry.getExpirationDate())
                .build();
    }
}
