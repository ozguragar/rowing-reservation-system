package com.rowingclub.backend.dto;

import lombok.Data;

/**
 * Admin edit of a user's role and/or member type. Both fields are optional —
 * only non-null values are applied.
 */
@Data
public class UpdateUserRequest {
    private String role;
    private String memberType;
}
