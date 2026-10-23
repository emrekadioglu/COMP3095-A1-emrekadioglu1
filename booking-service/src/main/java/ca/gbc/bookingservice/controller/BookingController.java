package ca.gbc.bookingservice.controller;

import ca.gbc.bookingservice.dto.BookingRequest;
import ca.gbc.bookingservice.dto.BookingResponse;
import ca.gbc.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @GetMapping
    public List<BookingResponse> getAllBookings(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long userId) {
        return bookingService.getAllBookings(roomId, userId);
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(@PathVariable String id) {
        return bookingService.getBookingById(id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateBooking(@PathVariable String id, @Valid @RequestBody BookingRequest request) {
        bookingService.updateBooking(id, request);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable String id) {
        return bookingService.cancelBooking(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBooking(@PathVariable String id) {
        bookingService.deleteBooking(id);
    }
}
