# COMP 3095 Assignment 1 – CampusSpace

Three independent Spring Boot microservices for a campus room-booking platform.
Gradle (Kotlin DSL) multi-module project, Java 21, Spring Boot 3.5.x.

## Group members

| Name | Student number 
|------|----------------|-----------------|
| Emre Kadioglu | 101443133 | ` 

## Services

| Service | Port | Data store | Database | Login |
|---------|------|------------|----------|-------|
| room-service | 8082 | PostgreSQL 16.4 (Flyway) | `room_service` | `room_service_user` |
| user-service | 8083 | PostgreSQL 16.4 (Flyway) | `user_service` | `user_service_user` |
| booking-service | 8084 | MongoDB 7.0.14 | `booking-service` | `booking_service_user` (readWrite on that DB only) |

The services do not call each other yet (Assignment 2). `booking-service` stores `roomId` / `userId` without checking them.

## Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle is not needed – the wrapper (`./gradlew`) is included

## Run everything with Docker Compose

```bash
docker compose down -v          # optional: start from empty volumes
docker compose up -d --build
docker compose ps               # all services Up, postgres and mongodb (healthy)
```

The init scripts in `init/` run only when the volumes are empty, which is why `down -v` is needed after changing them.

## Run a service from IntelliJ

1. Start only the databases: `docker compose up -d postgres mongodb`
2. Run the service's `*Application` class. The default Spring profile is `local`
   (databases on `localhost`). Compose uses the `docker` profile (databases on the Docker network).

## Run the tests

```bash
./gradlew test
```

Docker must be running: the integration tests use Testcontainers (`@ServiceConnection`) with the same
image tags as `docker-compose.yml` (`postgres:16.4`, `mongo:7.0.14`). Each service has a `@WebMvcTest`
slice test and a `@SpringBootTest` integration test. Seed data is cleared in `@BeforeEach`.

## API summary

**room-service** – `POST/GET /api/rooms`, `GET/PUT/DELETE /api/rooms/{id}`; filters `?building=&type=&minCapacity=`

**user-service** – `POST/GET /api/users`, `GET/PUT/DELETE /api/users/{id}`; filter `?role=`

**booking-service** – `POST/GET /api/bookings` (filters `?roomId=&userId=`), `GET/PUT/DELETE /api/bookings/{id}`, `PATCH /api/bookings/{id}/cancel`

Errors: 400 invalid input / unknown enum value, 404 unknown id, 409 duplicate or booking conflict.

## Postman

Import `postman/campusspace.postman_collection.json` and run the folders in order
(room → user → booking). It covers every endpoint and every business-rule error; booking dates are generated
by a pre-request script so they are always in the future.

## Known issues

<!-- Keep this identical to the "Known issues and deviations" table in your checklist. -->
- Replace this line with the issues you actually found when you ran the fresh-clone test.
