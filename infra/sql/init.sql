-- ============================================================
-- Admission System - Database Schema & Seed Data
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- events
CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- schedules
CREATE TABLE schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID         NOT NULL REFERENCES events(id),
    start_at    TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedules_event_id ON schedules(event_id);

-- seats
CREATE TABLE seats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID         NOT NULL REFERENCES events(id),
    zone        VARCHAR(10)  NOT NULL,
    seat_no     INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (event_id, zone, seat_no)
);

CREATE INDEX idx_seats_event_zone ON seats(event_id, zone);

-- holds
CREATE TABLE holds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID         NOT NULL REFERENCES schedules(id),
    seat_id         UUID         NOT NULL REFERENCES seats(id),
    client_id       VARCHAR(100) NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (schedule_id, seat_id),
    UNIQUE (schedule_id, client_id)
);

CREATE INDEX idx_holds_expires ON holds(expires_at)
    WHERE expires_at IS NOT NULL;

-- reservations
CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID         NOT NULL REFERENCES schedules(id),
    seat_id         UUID         NOT NULL REFERENCES seats(id),
    client_id       VARCHAR(100) NOT NULL,
    confirmed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (schedule_id, seat_id)
);

CREATE INDEX idx_reservations_client ON reservations(schedule_id, client_id);

-- ============================================================
-- Seed Data
-- ============================================================

-- Event
INSERT INTO events (id, name)
VALUES ('a0000000-0000-0000-0000-000000000001', '2026 New Year Concert');

-- Schedules (4 train times)
INSERT INTO schedules (id, event_id, start_at) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', '2026-01-30T06:00:00+09:00'),
  ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', '2026-01-30T09:00:00+09:00'),
  ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', '2026-01-30T13:00:00+09:00'),
  ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001', '2026-01-30T17:00:00+09:00');

-- Seats: Zone A (1~50), B (1~50), C (1~50), D (1~50) = 200 seats
INSERT INTO seats (event_id, zone, seat_no)
SELECT 'a0000000-0000-0000-0000-000000000001', 'A', generate_series(1, 50);

INSERT INTO seats (event_id, zone, seat_no)
SELECT 'a0000000-0000-0000-0000-000000000001', 'B', generate_series(1, 50);

INSERT INTO seats (event_id, zone, seat_no)
SELECT 'a0000000-0000-0000-0000-000000000001', 'C', generate_series(1, 50);

INSERT INTO seats (event_id, zone, seat_no)
SELECT 'a0000000-0000-0000-0000-000000000001', 'D', generate_series(1, 50);
