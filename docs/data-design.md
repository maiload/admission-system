# Data Design

## 1. Redis Key Design & Lua Scripts

### 1.1 Cluster Hash Tag Convention

모든 키는 `{eventId}`를 해시태그로 사용하여 동일 슬롯에 배치한다.
Lua 스크립트에서 다중 키 접근 시 같은 슬롯에 있어야 CROSSSLOT 에러를 방지할 수 있다.

> 문서 내에서는 가독성을 위해 `{evt}:{sch}`로 축약하되, 실제 해시태그는 `{evt}`만 적용된다.

### 1.2 Key Inventory

#### Queue Gate Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `q:{evt}:{sch}:z` | ZSET | 없음 (이벤트 종료 시 정리) | 대기열. member=queueToken, score=estimatedRank*10+tieBreaker |
| `qstate:{evt}:{sch}:{queueToken}` | HASH | 30분 | 대기열 토큰 상태. fields: status, estimatedRank, clientId, enterToken, jti |
| `qjoin:{evt}:{sch}:{clientId}` | STRING | 30분 | 중복 join 방지. value=queueToken |

#### Admission Worker Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `enter:{evt}:{sch}:{jti}` | STRING | 120초 | enter_token 저장. value=clientId |
| `rate:{evt}:{sch}:{epochSecond}` | STRING | 3초 | 초당 발급 카운터 |
| `active:{evt}:{sch}` | SET | 없음 (스케줄러 정리) | 코어 활성 세션. member=clientId |

#### Ticketing Core Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `cs:{evt}:{sch}:{sessionId}` | STRING | 300초 (슬라이딩) | Core session. value=clientId |
| `csidx:{evt}:{sch}:{clientId}` | STRING | 300초 (슬라이딩) | 역인덱스. value=sessionId |

#### Global Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `soldout:{evt}:{sch}` | STRING | 86400초 (24h) | 매진 플래그. value="1" |
| `active_schedules` | ZSET | 없음 | 활성 스케줄 목록. member={eventId}:{scheduleId}, score=startAtMs |

### 1.3 Lua Script #1: Queue Join

**File**: `queue-join.lua`

**Purpose**: 중복 등록 방지 + 상태 저장 + 대기열 추가를 원자적으로 처리

#### Keys

```
KEYS[1] = qjoin:{evt}:{sch}:{clientId}     -- 중복 체크 키
KEYS[2] = q:{evt}:{sch}:z                   -- 대기열 ZSET
KEYS[3] = qstate:{evt}:{sch}:{queueToken}   -- 상태 HASH
```

#### Args

```
ARGV[1] = clientId
ARGV[2] = queueToken
ARGV[3] = score            -- estimatedRank * 10 + tieBreaker
ARGV[4] = estimatedRank
ARGV[5] = ttlSec           -- qstate/qjoin TTL (1800)
```

#### Pseudocode

```lua
-- 1. 중복 체크
local existing = redis.call('GET', KEYS[1])
if existing then
    -- 멱등: 기존 queueToken 반환
    local rank = redis.call('HGET', 'qstate:...:' .. existing, 'estimatedRank')
    return { 'EXISTING', existing, rank }
end

-- 2. ZSET에 추가
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])

-- 3. 상태 HASH 생성
redis.call('HSET', KEYS[3],
    'status', 'WAITING',
    'estimatedRank', ARGV[4],
    'clientId', ARGV[1],
    'enterToken', '',
    'jti', ''
)
redis.call('EXPIRE', KEYS[3], ARGV[5])

-- 4. 중복 방지 키 세팅
redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[5])

return { 'CREATED', ARGV[2], ARGV[4] }
```

#### Return

```
[0] = "EXISTING" | "CREATED"
[1] = queueToken
[2] = estimatedRank
```

### 1.4 Lua Script #2: Admission Issue

**File**: `admission-issue.lua`

**Purpose**: 멀티 워커 환경에서 상위 N명 선별 + enter_token 발급 + 상태 변경 + 제한 체크를 원자적으로 처리

#### Keys

```
KEYS[1] = q:{evt}:{sch}:z                    -- 대기열 ZSET
KEYS[2] = rate:{evt}:{sch}:{epochSecond}      -- 초당 발급 카운터
KEYS[3] = active:{evt}:{sch}                  -- 활성 세션 SET
```

#### Args

```
ARGV[1] = maxIssue          -- 이번 호출 최대 발급 수
ARGV[2] = rateCap           -- 초당 발급 상한 (e.g. 200)
ARGV[3] = concurrencyCap    -- 동시 입장 상한 (e.g. 10000)
ARGV[4] = enterTtlSec       -- enter_token TTL (120)
ARGV[5] = qstateTtlSec      -- qstate TTL 연장 (1800)
ARGV[6] = rateTtlSec        -- rate counter TTL (3)
-- ARGV[7..7+N*2] = pairs of (jti, enterTokenValue) for pre-generated tokens
```

> **Note**: jti와 enterToken은 서버(Java)에서 사전 생성하여 ARGV로 전달한다.
> Lua 내에서 UUID 생성이 불가하므로 이 방식이 필수.

#### Pseudocode

```lua
-- 1. 현재 제한 확인
local currentRate = tonumber(redis.call('GET', KEYS[2]) or '0')
local currentActive = redis.call('SCARD', KEYS[3])
local rateRemaining = rateCap - currentRate
local capacityRemaining = concurrencyCap - currentActive

-- 2. 실제 발급 가능 수 계산
local issueCount = math.min(maxIssue, rateRemaining, capacityRemaining)
if issueCount <= 0 then
    return { 0, 0, redis.call('ZCARD', KEYS[1]) }
end

-- 3. ZPOPMIN으로 상위 N명 꺼내기
local popped = redis.call('ZPOPMIN', KEYS[1], issueCount)
-- popped = { member1, score1, member2, score2, ... }

local issued = {}
local tokenIdx = 7  -- ARGV에서 jti/token 시작 인덱스

for i = 1, #popped, 2 do
    local queueToken = popped[i]
    local jti = ARGV[tokenIdx]
    local enterTokenValue = ARGV[tokenIdx + 1]
    tokenIdx = tokenIdx + 2

    local qstateKey = -- construct from queueToken

    -- 4-a. qstate 상태 변경
    redis.call('HSET', qstateKey,
        'status', 'ADMISSION_GRANTED',
        'enterToken', enterTokenValue,
        'jti', jti
    )
    redis.call('EXPIRE', qstateKey, qstateTtlSec)

    -- 4-b. enter_token 키 생성
    local clientId = redis.call('HGET', qstateKey, 'clientId')
    local enterKey = -- construct enter:{evt}:{sch}:{jti}
    redis.call('SET', enterKey, clientId, 'EX', enterTtlSec)

    -- 4-c. active SET에 추가
    redis.call('SADD', KEYS[3], clientId)

    table.insert(issued, { queueToken, jti, enterTokenValue })
end

-- 5. rate counter 증가
redis.call('INCRBY', KEYS[2], #issued)
if currentRate == 0 then
    redis.call('EXPIRE', KEYS[2], rateTtlSec)
end

local remaining = redis.call('ZCARD', KEYS[1])
return { #issued, 0, remaining }
```

#### Return

```
[0] = issuedCount
[1] = skippedCount
[2] = remainingQueueSize
```

#### Note on Key Construction in Lua

Lua 스크립트 내에서 `qstateKey`, `enterKey` 등을 동적으로 조합해야 한다.
이때 `{evt}:{sch}` prefix는 ARGV로 추가 전달하여 키를 조합한다.

실제 구현 시 ARGV 구조:

```
ARGV[1]  = maxIssue
ARGV[2]  = rateCap
ARGV[3]  = concurrencyCap
ARGV[4]  = enterTtlSec
ARGV[5]  = qstateTtlSec
ARGV[6]  = rateTtlSec
ARGV[7]  = keyPrefix          -- "{evt}:{sch}" (키 조합용)
ARGV[8]  = tokenCount         -- 사전 생성된 토큰 수
ARGV[9..] = jti1, token1, jti2, token2, ...
```

### 1.5 Lua Script #3: Core Handshake

**File**: `core-handshake.lua`

**Purpose**: enter_token DEL + core session SET + active SADD를 원자적으로 처리

#### Keys

```
KEYS[1] = enter:{evt}:{sch}:{jti}             -- enter_token
KEYS[2] = cs:{evt}:{sch}:{sessionId}           -- core session
KEYS[3] = csidx:{evt}:{sch}:{clientId}         -- 역인덱스
KEYS[4] = active:{evt}:{sch}                   -- 활성 세션 SET
```

#### Args

```
ARGV[1] = clientId
ARGV[2] = sessionId
ARGV[3] = sessionTtlSec     -- 300
```

#### Pseudocode

```lua
-- 1. enter_token 확인
local storedClientId = redis.call('GET', KEYS[1])
if not storedClientId then
    return { 'INVALID' }
end

-- 2. clientId 일치 확인
if storedClientId ~= ARGV[1] then
    return { 'MISMATCH' }
end

-- 3. enter_token 1회 소모
redis.call('DEL', KEYS[1])

-- 4. 기존 세션 정리 (같은 clientId의 이전 세션)
local oldSessionId = redis.call('GET', KEYS[3])
if oldSessionId then
    redis.call('DEL', 'cs:' .. keyPrefix .. ':' .. oldSessionId)
end

-- 5. 새 세션 생성
redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])
redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[3])

-- 6. active SET에 추가 (이미 있으면 무시)
redis.call('SADD', KEYS[4], ARGV[1])

return { 'OK', ARGV[2] }
```

#### Return

```
[0] = "OK" | "INVALID" | "MISMATCH"
[1] = sessionId (OK인 경우)
```

### 1.6 Key Lifecycle Summary

```
[User clicks "예매하기"]
  → qjoin:{cid}        STRING  TTL 30min  (중복 방지)
  → q:{z}              ZSET    member     (대기열)
  → qstate:{qt}        HASH    TTL 30min  (상태: WAITING)

[Admission Worker polls]
  → q:{z}              ZPOPMIN            (대기열에서 제거)
  → qstate:{qt}        HASH    상태→ADMISSION_GRANTED
  → enter:{jti}        STRING  TTL 120s   (입장권)
  → rate:{epoch}       STRING  TTL 3s     (발급 카운터)
  → active:{set}       SADD               (활성 세션)

[User enters Core]
  → enter:{jti}        DEL                (1회 소모)
  → cs:{sid}           STRING  TTL 300s   (Core 세션)
  → csidx:{cid}        STRING  TTL 300s   (역인덱스)

[User confirms seat]
  → active:{set}       SREM               (세션 종료)
  → cs:{sid}           DEL
  → csidx:{cid}        DEL
  → soldout:{flag}     SET (if 매진)

[Session expires without confirm]
  → cs:{sid}           TTL 만료 → 자동 삭제
  → csidx:{cid}        TTL 만료 → 자동 삭제
  → active:{set}       스케줄러가 SREM     (정리)
```

---

## 2. Database Schema (PostgreSQL + R2DBC)

### 2.1 ID Generation Strategy

- 모든 PK: `UUID` (pgcrypto `gen_random_uuid()` 또는 앱에서 생성)
- R2DBC 논블로킹 특성상 SEQUENCE 대비 UUID가 적합

### 2.2 DDL

#### events

```sql
CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
```

#### schedules

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

#### seats

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

#### holds

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

#### reservations

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

### 2.3 ER Diagram

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

### 2.4 Data Access Patterns

#### Query by Module

| Module | Query | Index Used |
|--------|-------|-----------|
| Core: 좌석 조회 | `SELECT s.*, h.id IS NOT NULL as held, r.id IS NOT NULL as confirmed FROM seats s LEFT JOIN holds h ... LEFT JOIN reservations r ... WHERE s.event_id = ? ORDER BY s.zone, s.seat_no` | `idx_seats_event_zone` |
| Core: 홀드 생성 | `INSERT INTO holds (schedule_id, seat_id, client_id, expires_at) VALUES (?, ?, ?, ?)` | UNIQUE constraints |
| Core: 홀드 확인 | `SELECT * FROM holds WHERE id = ? AND client_id = ?` | PK |
| Core: 확정 | `INSERT INTO reservations (...) VALUES (...)` + `DELETE FROM holds WHERE id = ?` | PK, UNIQUE |
| Core: 만료 정리 | `DELETE FROM holds WHERE expires_at < now()` | `idx_holds_expires` |
| Core: 매진 확인 | `SELECT COUNT(*) FROM seats WHERE event_id = ? AND id NOT IN (SELECT seat_id FROM holds WHERE schedule_id = ?) AND id NOT IN (SELECT seat_id FROM reservations WHERE schedule_id = ?)` | composite indexes |

### 2.5 R2DBC Access Layer

- `DatabaseClient` + `R2dbcEntityTemplate` 사용
- jOOQ 코드 생성기 미사용 (테이블 5개 규모에 오버엔지니어링)
- 테이블/컬럼명 상수는 수동 관리 (`common` 모듈에 상수 클래스)

### 2.6 Demo Seed Data

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

---

## 3. Domain Model & State Machines

### 3.1 Domain Entities

#### Queue Domain (Redis-only)

```
QueueToken
├── queueToken: String       (고유 식별자, "qt_" prefix)
├── clientId: String         (쿠키 기반 사용자 식별)
├── eventId: String
├── scheduleId: String
├── estimatedRank: Long
├── status: QueueStatus      (WAITING | ADMISSION_GRANTED | EXPIRED | SOLD_OUT)
├── enterToken: String?      (ADMISSION_GRANTED 시 발급)
└── jti: String?             (enter_token의 JWT ID)
```

#### Admission Domain (Redis-only)

```
EnterToken
├── jti: String              (고유 식별자, UUID)
├── clientId: String
├── eventId: String
├── scheduleId: String
├── token: String            (base64url: uuid.hmac_sig)
├── expiresAtMs: Long
└── ttlSec: Integer          (120)

CoreSession
├── sessionId: String        (UUID)
├── clientId: String
├── eventId: String
├── scheduleId: String
├── token: String            ("cs_" + uuid + "." + hmac_sig)
├── ttlSec: Integer          (300)
└── lastRefreshMs: Long
```

#### Ticketing Domain (PostgreSQL)

```
Event
├── id: UUID
├── name: String
└── createdAt: Instant

Schedule
├── id: UUID
├── eventId: UUID
├── startAt: Instant
└── createdAt: Instant

Seat
├── id: UUID
├── eventId: UUID
├── zone: String
├── seatNo: Integer
└── createdAt: Instant

Hold
├── id: UUID
├── scheduleId: UUID
├── seatId: UUID
├── clientId: String
├── expiresAt: Instant
└── createdAt: Instant

Reservation
├── id: UUID
├── scheduleId: UUID
├── seatId: UUID
├── clientId: String
└── confirmedAt: Instant
```

### 3.2 State Machines

#### 3.2.1 Queue Token State Machine

```
                          ┌─────────────────────────────┐
                          │                             │
                          ▼                             │
    ┌──────────┐    ┌──────────┐    ┌────────────────┐  │
    │  (join)  │───▶│ WAITING  │───▶│ADMISSION_      │  │
    │          │    │          │    │GRANTED         │  │
    └──────────┘    └────┬─────┘    └───────┬────────┘  │
                         │                  │           │
                         │                  │           │
                         ▼                  ▼           │
                    ┌──────────┐      ┌──────────┐      │
                    │ SOLD_OUT │      │ EXPIRED  │◀─────┘
                    │          │      │          │  (enter_token
                    └──────────┘      └──────────┘   TTL 만료)
```

| Transition | Trigger | Actor |
|-----------|---------|-------|
| (없음) → WAITING | POST /gate/join | Gate |
| WAITING → ADMISSION_GRANTED | Admission Worker Lua | Worker |
| WAITING → SOLD_OUT | soldout 플래그 감지 | Gate (SSE push) |
| WAITING → EXPIRED | qstate TTL 만료 (30min) | Redis TTL |
| ADMISSION_GRANTED → EXPIRED | enter_token TTL 만료 (120s) | Redis TTL + Gate 감지 |

#### 3.2.2 Seat State Machine

```
    ┌───────────┐      ┌──────────┐      ┌───────────┐
    │ AVAILABLE │─────▶│  HELD    │─────▶│ CONFIRMED │
    │           │      │          │      │           │
    └───────────┘      └────┬─────┘      └───────────┘
          ▲                 │
          │                 │
          └─────────────────┘
           (hold 만료 / 삭제)
```

| Transition | Trigger | Mechanism |
|-----------|---------|-----------|
| AVAILABLE → HELD | POST /core/holds | DB INSERT (unique constraint) |
| HELD → CONFIRMED | POST /core/holds/{id}/confirm | DB Transaction (insert reservation + delete hold) |
| HELD → AVAILABLE | hold expires_at 초과 | @Scheduled 정리 스케줄러 (DELETE hold row) |

**Note**: 좌석 상태는 별도 컬럼이 아니라 holds/reservations 테이블의 존재 여부로 결정.

- holds에 row 존재 → HELD
- reservations에 row 존재 → CONFIRMED
- 둘 다 없음 → AVAILABLE

#### 3.2.3 Core Session Lifecycle

```
    ┌──────────┐      ┌──────────┐      ┌──────────┐
    │  (enter) │─────▶│  ACTIVE  │─────▶│  CLOSED  │
    │          │      │          │      │          │
    └──────────┘      └────┬─────┘      └──────────┘
                           │
                           │ TTL 만료 (5min 무활동)
                           ▼
                      ┌──────────┐
                      │ EXPIRED  │
                      └──────────┘
```

| Transition | Trigger | Mechanism |
|-----------|---------|-----------|
| (없음) → ACTIVE | POST /core/enter (handshake) | Core Handshake Lua |
| ACTIVE → ACTIVE (연장) | 매 Core API 호출 | EXPIRE 갱신 (슬라이딩) |
| ACTIVE → CLOSED | confirm 성공 | Redis DEL + SREM active |
| ACTIVE → EXPIRED | cs TTL 만료 (300s) | Redis TTL → 스케줄러 SREM active |

### 3.3 Token Formats

#### queueToken

```
Purpose: 대기열 식별
Format:  "qt_" + UUID v4
Storage: Redis ZSET member + HASH key
TTL:     qstate HASH TTL = 30분
```

#### enterToken

```
Purpose: Core 입장 자격 증명 (1회용)
Format:  base64url(uuid + "." + hmac(uuid, eventId, scheduleId, expMs))
Secret:  ENTER_TOKEN_SECRET (환경변수)
Storage: Redis STRING enter:{evt}:{sch}:{jti} = clientId, TTL 120s
```

#### coreSessionToken

```
Purpose: Core API 인증 (슬라이딩 세션)
Format:  "cs_" + uuid + "." + hmac(uuid, eventId, scheduleId, expMs)
Secret:  CORE_SESSION_SECRET (환경변수)
Storage: Redis STRING cs:{evt}:{sch}:{sessionId} = clientId, TTL 300s
```

### 3.4 Configuration Constants

#### Timeouts & TTLs

| Parameter | Value | Location |
|-----------|-------|----------|
| queueToken/qstate TTL | 60초 | application.yml |
| enterToken TTL | 120초 | application.yml |
| coreSession TTL | 300초 (5분, 슬라이딩) | application.yml |
| hold TTL | 60초 | application.yml |
| rate counter TTL | 3초 | application.yml |
| soldout 플래그 TTL | 86400초 (24h) | application.yml |
| clientId 쿠키 Max-Age | 1일 | application.yml |

#### Capacity Limits

| Parameter | Default Value | Description |
|-----------|--------------|-------------|
| rateCap | 200/s | 초당 입장권 발급 상한 |
| concurrencyCap | 10,000 | Core 동시 입장자 상한 |
| maxBatch | 200 | 워커 1회 최대 발급 수 |
| workerPollIntervalMs | 200 | 워커 폴링 주기 |

#### Scheduler Intervals

| Scheduler | Interval | Module |
|-----------|---------|--------|
| Hold 만료 정리 | 1~2초 | ticketing-core |
| Active SET 정리 | 5~10초 | ticketing-core |
