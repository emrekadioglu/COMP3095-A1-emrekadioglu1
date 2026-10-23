package ca.gbc.bookingservice.repository;

import ca.gbc.bookingservice.model.Booking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends MongoRepository<Booking, String> {

    List<Booking> findByRoomId(Long roomId);

    List<Booking> findByUserId(Long userId);

    List<Booking> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("{ 'roomId': ?0, 'status': 'CONFIRMED', 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 }, '_id': { $ne: ?3 } }")
    List<Booking> findOverlappingBookings(Long roomId, Instant startTime, Instant endTime, String excludeId);
}
