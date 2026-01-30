# 5. Domain Model & State Machines

## 5.1 Domain Entities

### Queue Domain (Redis-only)

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

### Admission Domain (Redis-only)

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

### Ticketing Domain (PostgreSQL)

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

---

## 5.2 State Machines

### 5.2.1 Queue Token State Machine

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

---

### 5.2.2 Seat State Machine

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

---

### 5.2.3 Core Session Lifecycle

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

---

## 5.3 Token Formats

### syncToken

```
Purpose: join 요청 조작 방지
Format:  base64url(payload + "." + hmac_sig)
Payload: eventId | scheduleId | startAtMs | issuedAtMs | nonce(UUID)
Secret:  SYNC_TOKEN_SECRET (환경변수)
TTL:     startAtMs + 15초까지 유효
```

### queueToken

```
Purpose: 대기열 식별
Format:  "qt_" + UUID v4
Storage: Redis ZSET member + HASH key
TTL:     qstate HASH TTL = 30분
```

### enterToken

```
Purpose: Core 입장 자격 증명 (1회용)
Format:  base64url(uuid + "." + hmac(uuid, eventId, scheduleId, expMs))
Secret:  ENTER_TOKEN_SECRET (환경변수)
Storage: Redis STRING enter:{evt}:{sch}:{jti} = clientId, TTL 120s
```

### coreSessionToken

```
Purpose: Core API 인증 (슬라이딩 세션)
Format:  "cs_" + uuid + "." + hmac(uuid, eventId, scheduleId, expMs)
Secret:  CORE_SESSION_SECRET (환경변수)
Storage: Redis STRING cs:{evt}:{sch}:{sessionId} = clientId, TTL 300s
```

---

## 5.4 Configuration Constants

### Timeouts & TTLs

| Parameter | Value | Location |
|-----------|-------|----------|
| syncToken 유효기간 | startAtMs + 15s | application.yml |
| join 허용 윈도우 | startAtMs - 2s ~ startAtMs + 10s | application.yml |
| queueToken/qstate TTL | 30분 (1800s) | application.yml |
| enterToken TTL | 120초 | application.yml |
| coreSession TTL | 300초 (5분, 슬라이딩) | application.yml |
| hold TTL | 60초 | application.yml |
| rate counter TTL | 3초 | application.yml |
| soldout 플래그 TTL | 86400초 (24h) | application.yml |
| clientId 쿠키 Max-Age | 7일 | application.yml |

### Capacity Limits

| Parameter | Default Value | Description |
|-----------|--------------|-------------|
| rateCap | 200/s | 초당 입장권 발급 상한 |
| concurrencyCap | 10,000 | Core 동시 입장자 상한 |
| maxBatch | 200 | 워커 1회 최대 발급 수 |
| workerPollIntervalMs | 200 | 워커 폴링 주기 |

### Scheduler Intervals

| Scheduler | Interval | Module |
|-----------|---------|--------|
| Hold 만료 정리 | 1~2초 | ticketing-core |
| Active SET 정리 | 5~10초 | ticketing-core |
