package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminMoveRequest {
    @NotNull
    private Long userId;
    @NotNull
    private Long fromBoatId;
    @NotNull
    private Long toBoatId;
}
