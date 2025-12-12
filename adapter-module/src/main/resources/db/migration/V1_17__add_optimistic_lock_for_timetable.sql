ALTER TABLE prototype_reservation.timetable
ADD COLUMN version BIGINT;

ALTER TABLE prototype_reservation.outbox
ADD COLUMN count INT;
