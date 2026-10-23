package ca.gbc.roomservice.dto;

import ca.gbc.roomservice.model.RoomFeature;
import ca.gbc.roomservice.model.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoomRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank String name,
        @NotBlank String building,
        @Min(1) int capacity,
        @NotNull RoomType type,
        boolean accessible,
        Set<RoomFeature> features
) {}
