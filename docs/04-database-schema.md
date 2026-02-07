# 4. Database Schema (PostgreSQL + R2DBC)

## 4.1 ID Generation Strategy

- 모든 PK: `UUID` (pgcrypto `gen_random_uuid()` 또는 앱에서 생성)
- R2DBC 논블로킹 특성상 SEQUENCE 대비 UUID가 적합

## 4.2 DDL

### events

```sql
CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
```

### schedules

```sql
CREATE TABLE schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID            NOT NULL REFERENCES events(id),
    start_at    TIMESTAMPTZ     NOT NULL,
    train_name  VARCHAR(50)     NOT NULL,
    train_number VARCHAR(20)    NOT NULL,
    departure   VARCHAR(50)     NOT NULL,
    arrival     VARCHAR(50)     NOT NULL,
    departure_time TIME         NOT NULL,
    arrival_time   TIME         NOT NULL,
    service_date   DATE         NOT NULL,
    price       INTEGER         NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedules_event_id ON schedules(event_id);
```

### seats

```sql
CREATE TABLE seats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID            NOT NULL REFERENCES events(id),
    zone        VARCHAR(10)     NOT NULL,
    seat_no     INTEGER         NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    UNIQUE (event_id, zone, seat_no)
);

CREATE INDEX idx_seats_event_zone ON seats(event_id, zone);
```

### holds

```sql
CREATE TABLE holds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID            NOT NULL REFERENCES schedules(id),
    seat_id         UUID            NOT NULL REFERENCES seats(id),
    client_id       VARCHAR(100)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    UNIQUE (schedule_id, seat_id),
    UNIQUE (schedule_id, client_id)
);

CREATE INDEX idx_holds_expires ON holds(expires_at)
    WHERE expires_at IS NOT NULL;
```

**Constraint Rationale**

| Constraint | Purpose |
|-----------|---------|
| `UNIQUE (schedule_id, seat_id)` | 동일 좌석 중복 홀드 방지 |
| `UNIQUE (schedule_id, client_id)` | 1인 1좌석 제한 |
| `idx_holds_expires` | 만료 정리 스케줄러 최적화 (partial index) |

### reservations

```sql
CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID            NOT NULL REFERENCES schedules(id),
    seat_id         UUID            NOT NULL REFERENCES seats(id),
    client_id       VARCHAR(100)    NOT NULL,
    confirmed_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),

    UNIQUE (schedule_id, seat_id)
);

CREATE INDEX idx_reservations_client ON reservations(schedule_id, client_id);
```

## 4.3 ER Diagram

```
┌──────────┐       ┌──────────────┐       ┌──────────┐
│  events   │1────N│  schedules   │       │  seats    │
│           │       │              │       │           │
│ id (PK)   │       │ id (PK)      │       │ id (PK)   │
│ name      │       │ event_id(FK) │       │ event_id  │
│ created_at│       │ start_at     │       │ zone      │
└──────────┘       │ created_at   │       │ seat_no   │
                    └──────┬───────┘       └─────┬─────┘
                           │                     │
                           │                     │
                    ┌──────┴─────────────────────┴──────┐
                    │                                    │
               ┌────┴────┐                        ┌─────┴──────┐
               │  holds   │                        │reservations│
               │          │                        │            │
               │ id (PK)  │                        │ id (PK)    │
               │ sched_id │                        │ sched_id   │
               │ seat_id  │                        │ seat_id    │
               │ client_id│                        │ client_id  │
               │ expires  │                        │ confirmed  │
               └──────────┘                        └────────────┘
```

## 4.4 Data Access Patterns

### Query by Module

| Module | Query | Index Used |
|--------|-------|-----------|
| Core: 좌석 조회 | `SELECT s.*, h.id IS NOT NULL as held, r.id IS NOT NULL as confirmed FROM seats s LEFT JOIN holds h ... LEFT JOIN reservations r ... WHERE s.event_id = ? ORDER BY s.zone, s.seat_no` | `idx_seats_event_zone` |
| Core: 홀드 생성 | `INSERT INTO holds (schedule_id, seat_id, client_id, expires_at) VALUES (?, ?, ?, ?)` | UNIQUE constraints |
| Core: 홀드 확인 | `SELECT * FROM holds WHERE id = ? AND client_id = ?` | PK |
| Core: 확정 | `INSERT INTO reservations (...) VALUES (...)` + `DELETE FROM holds WHERE id = ?` | PK, UNIQUE |
| Core: 만료 정리 | `DELETE FROM holds WHERE expires_at < now()` | `idx_holds_expires` |
| Core: 매진 확인 | `SELECT COUNT(*) FROM seats WHERE event_id = ? AND id NOT IN (SELECT seat_id FROM holds WHERE schedule_id = ?) AND id NOT IN (SELECT seat_id FROM reservations WHERE schedule_id = ?)` | composite indexes |

## 4.5 R2DBC Access Layer

- `DatabaseClient` + `R2dbcEntityTemplate` 사용
- jOOQ 코드 생성기 미사용 (테이블 5개 규모에 오버엔지니어링)
- 테이블/컬럼명 상수는 수동 관리 (`common` 모듈에 상수 클래스)

## 4.6 Demo Seed Data

```sql
-- Event
INSERT INTO events (id, name)
VALUES ('ev_2026_0101', '2026 New Year Concert');

-- Schedule
INSERT INTO schedules (
    id, event_id, start_at,
    train_name, train_number, departure, arrival,
    departure_time, arrival_time, service_date, price
)
VALUES (
    'sc_2026_0101_2000', 'ev_2026_0101', '2026-01-01T20:00:00+09:00',
    'KTX', '103', '서울', '부산',
    '09:00:00', '11:35:00', '2026-01-01', 59800
);

-- Seats: Zone A (1~100), Zone B (1~100) = 200 seats
INSERT INTO seats (event_id, zone, seat_no)
SELECT 'ev_2026_0101', 'A', generate_series(1, 100);

INSERT INTO seats (event_id, zone, seat_no)
SELECT 'ev_2026_0101', 'B', generate_series(1, 100);
```
