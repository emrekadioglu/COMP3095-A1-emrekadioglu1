package ca.gbc.bookingservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    private String id;

    private Long roomId;

    private Long userId;

    private Instant startTime;

    private Instant endTime;

    private String purpose;

    private int attendees;

    private BookingStatus status;

    private Instant createdAt;
}
