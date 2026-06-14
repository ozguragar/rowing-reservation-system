package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CopyWeekRequest {
    @NotNull
    private LocalDate sourceWeekStart;
    @NotNull
    private LocalDate targetWeekStart;
}
