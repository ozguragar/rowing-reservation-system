package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {
    @NotNull
    private Long boatId;
    @NotNull
    private Long sessionId;
    private Boolean isCoxSeat;
}
