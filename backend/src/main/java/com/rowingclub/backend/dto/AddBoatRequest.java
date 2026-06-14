package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddBoatRequest {
    @NotNull
    private String type;
    @NotNull
    private Integer capacity;
    private Boolean isBasicTrainingBoat = false;
    private Boolean hasCoxSeat = false;
    private String name;
}
