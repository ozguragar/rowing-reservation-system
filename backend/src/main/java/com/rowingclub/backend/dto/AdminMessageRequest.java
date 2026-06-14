package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminMessageRequest {
    @NotNull
    private Long ledgerEntryId;
    @NotBlank
    private String message;
}
