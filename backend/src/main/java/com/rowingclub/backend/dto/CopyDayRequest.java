package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CopyDayRequest {
    @NotNull
    private LocalDate sourceDate;
    @NotNull
    private LocalDate targetDate;
}
