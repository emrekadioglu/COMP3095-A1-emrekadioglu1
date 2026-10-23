package ca.gbc.roomservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    @Column(nullable = false)
    private boolean accessible;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_feature", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "feature")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<RoomFeature> features = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
