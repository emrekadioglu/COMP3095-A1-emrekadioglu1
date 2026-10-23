package ca.gbc.bookingservice.dto;

import ca.gbc.bookingservice.model.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        String id,
        Long roomId,
        Long userId,
        Instant startTime,
        Instant endTime,
        String purpose,
        int attendees,
        BookingStatus status,
        Instant createdAt
) {}
