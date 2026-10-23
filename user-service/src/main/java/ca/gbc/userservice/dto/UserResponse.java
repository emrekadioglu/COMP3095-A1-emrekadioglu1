package ca.gbc.userservice.dto;

import ca.gbc.userservice.model.UserRole;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        String department,
        boolean active
) {}
