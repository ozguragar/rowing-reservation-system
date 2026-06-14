package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class BulkSessionRequest {
    @NotNull
    private List<CreateSessionRequest> sessions;
}
