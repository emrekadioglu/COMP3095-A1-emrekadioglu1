package ca.gbc.bookingservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookingRequest(
        @NotNull Long roomId,
        @NotNull Long userId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotBlank String purpose,
        @Min(1) int attendees
) {}
