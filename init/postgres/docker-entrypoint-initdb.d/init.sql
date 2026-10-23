-- Create users and databases for room-service and user-service
CREATE USER room_service_user WITH PASSWORD 'password';
CREATE DATABASE room_service OWNER room_service_user;

CREATE USER user_service_user WITH PASSWORD 'password';
CREATE DATABASE user_service OWNER user_service_user;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE room_service TO room_service_user;
GRANT ALL PRIVILEGES ON DATABASE user_service TO user_service_user;
