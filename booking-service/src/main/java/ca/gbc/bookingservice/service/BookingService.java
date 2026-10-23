package ca.gbc.bookingservice.service;

import ca.gbc.bookingservice.dto.BookingRequest;
import ca.gbc.bookingservice.dto.BookingResponse;
import ca.gbc.bookingservice.exception.ConflictException;
import ca.gbc.bookingservice.exception.ResourceNotFoundException;
import ca.gbc.bookingservice.model.Booking;
import ca.gbc.bookingservice.model.BookingStatus;
import ca.gbc.bookingservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingResponse createBooking(BookingRequest request) {
        validateTimes(request.startTime(), request.endTime());
        checkOverlap(request.roomId(), request.startTime(), request.endTime(), null);

        Booking booking = Booking.builder()
                .roomId(request.roomId())
                .userId(request.userId())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .purpose(request.purpose())
                .attendees(request.attendees())
                .status(BookingStatus.CONFIRMED)
                .createdAt(Instant.now())
                .build();
        Booking saved = bookingRepository.save(booking);
        log.info("Created booking with id {}", saved.getId());
        return toResponse(saved);
    }

    public List<BookingResponse> getAllBookings(Long roomId, Long userId) {
        List<Booking> bookings;
        if (roomId != null && userId != null) {
            bookings = bookingRepository.findByRoomIdAndUserId(roomId, userId);
        } else if (roomId != null) {
            bookings = bookingRepository.findByRoomId(roomId);
        } else if (userId != null) {
            bookings = bookingRepository.findByUserId(userId);
        } else {
            bookings = bookingRepository.findAll();
        }
        return bookings.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BookingResponse getBookingById(String id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        return toResponse(booking);
    }

    public void updateBooking(String id, BookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Cannot update a cancelled booking");
        }
        validateTimes(request.startTime(), request.endTime());
        checkOverlap(request.roomId(), request.startTime(), request.endTime(), id);

        booking.setRoomId(request.roomId());
        booking.setUserId(request.userId());
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        booking.setPurpose(request.purpose());
        booking.setAttendees(request.attendees());
        bookingRepository.save(booking);
        log.info("Updated booking with id {}", id);
    }

    public BookingResponse cancelBooking(String id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking is already cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        log.info("Cancelled booking with id {}", id);
        return toResponse(saved);
    }

    public void deleteBooking(String id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Booking not found with id " + id);
        }
        bookingRepository.deleteById(id);
        log.info("Deleted booking with id {}", id);
    }

    private void validateTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (start.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Start time must be in the future");
        }
    }

    private void checkOverlap(Long roomId, Instant start, Instant end, String excludeId) {
        String exclude = excludeId != null ? excludeId : "000000000000000000000000";
        List<Booking> overlapping = bookingRepository.findOverlappingBookings(roomId, start, end, exclude);
        if (!overlapping.isEmpty()) {
            throw new ConflictException("Overlapping booking exists for this room");
        }
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoomId(),
                booking.getUserId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getPurpose(),
                booking.getAttendees(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
