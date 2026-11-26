package com.satvault.dto;

import com.satvault.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class AuthRequests {

    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Valid email is required")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
            String password
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Valid email is required")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record UserResponse(
            boolean authenticated,
            Long id,
            String fullName,
            String email,
            Instant createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(true, user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
        }

        public static UserResponse anonymous() {
            return new UserResponse(false, null, null, null, null);
        }
    }
}
