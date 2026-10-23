CREATE TABLE room (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    building VARCHAR(255) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity >= 1),
    type VARCHAR(50) NOT NULL,
    accessible BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE room_feature (
    room_id BIGINT NOT NULL REFERENCES room(id) ON DELETE CASCADE,
    feature VARCHAR(50) NOT NULL,
    PRIMARY KEY (room_id, feature)
);
