package ca.gbc.roomservice.service;

import ca.gbc.roomservice.dto.RoomRequest;
import ca.gbc.roomservice.dto.RoomResponse;
import ca.gbc.roomservice.exception.DuplicateResourceException;
import ca.gbc.roomservice.exception.ResourceNotFoundException;
import ca.gbc.roomservice.model.Room;
import ca.gbc.roomservice.model.RoomFeature;
import ca.gbc.roomservice.model.RoomType;
import ca.gbc.roomservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final RoomRepository roomRepository;

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        if (roomRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Room with code " + request.code() + " already exists");
        }
        Room room = Room.builder()
                .code(request.code())
                .name(request.name())
                .building(request.building())
                .capacity(request.capacity())
                .type(request.type())
                .accessible(request.accessible())
                .features(request.features() != null ? new HashSet<>(request.features()) : new HashSet<>())
                .active(true)
                .build();
        Room saved = roomRepository.save(room);
        log.info("Created room with id {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms(String building, String typeStr, Integer minCapacity) {
        RoomType type = null;
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                type = RoomType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown room type: " + typeStr);
            }
        }
        Specification<Room> spec = (root, query, cb) -> cb.conjunction();
        if (building != null && !building.isBlank()) {
            String b = building;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("building"), b));
        }
        if (type != null) {
            RoomType t = type;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), t));
        }
        if (minCapacity != null) {
            int min = minCapacity;
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacity"), min));
        }
        return roomRepository.findAll(spec, Sort.by("id"))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id " + id));
        return toResponse(room);
    }

    @Transactional
    public void updateRoom(Long id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id " + id));
        if (!room.getCode().equals(request.code()) && roomRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Room with code " + request.code() + " already exists");
        }
        room.setCode(request.code());
        room.setName(request.name());
        room.setBuilding(request.building());
        room.setCapacity(request.capacity());
        room.setType(request.type());
        room.setAccessible(request.accessible());
        room.setFeatures(request.features() != null ? new HashSet<>(request.features()) : new HashSet<>());
        roomRepository.save(room);
        log.info("Updated room with id {}", id);
    }

    @Transactional
    public void deleteRoom(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new ResourceNotFoundException("Room not found with id " + id);
        }
        roomRepository.deleteById(id);
        log.info("Deleted room with id {}", id);
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getCode(),
                room.getName(),
                room.getBuilding(),
                room.getCapacity(),
                room.getType(),
                room.isAccessible(),
                room.getFeatures(),
                room.isActive()
        );
    }
}
