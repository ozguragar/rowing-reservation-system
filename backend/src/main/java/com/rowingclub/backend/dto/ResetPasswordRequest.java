package com.rowingclub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank @Size(max = 200)
    private String token;
    @NotBlank @Size(min = 8, max = 128)
    private String newPassword;
}
