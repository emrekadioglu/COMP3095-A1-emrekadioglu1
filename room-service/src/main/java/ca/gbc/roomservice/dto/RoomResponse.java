package ca.gbc.roomservice.dto;

import ca.gbc.roomservice.model.RoomFeature;
import ca.gbc.roomservice.model.RoomType;

import java.util.Set;

public record RoomResponse(
        Long id,
        String code,
        String name,
        String building,
        int capacity,
        RoomType type,
        boolean accessible,
        Set<RoomFeature> features,
        boolean active
) {}
