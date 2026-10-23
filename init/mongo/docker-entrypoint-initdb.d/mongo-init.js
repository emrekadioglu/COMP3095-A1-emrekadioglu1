db = db.getSiblingDB('booking-service');
db.createUser({
  user: 'booking_service_user',
  pwd: 'password',
  roles: [
    { role: 'readWrite', db: 'booking-service' }
  ]
});
