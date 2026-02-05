# 2. API Specification

## 2.1 Error Response Format (공통)

모든 에러 응답은 아래 형식을 따른다.

```json
{
  "code": "SEAT_ALREADY_HELD",
  "message": "This seat is already held by another user.",
  "timestamp": 1738200123456
}
```

### Error Code Table

| HTTP Status | Code | Description |
|-------------|------|-------------|
| 400 | `INVALID_SYNC_TOKEN` | syncToken이 유효하지 않음 (변조/만료) |
| 400 | `TOO_EARLY` | startAt 이전 join 시도 (delta < 0) |
| 400 | `INVALID_WINDOW` | join 허용 시간대 초과 (startAt 이후 설정된 window 초과) |
| 400 | `INVALID_REQUEST` | 일반적인 요청 파라미터 오류 |
| 401 | `ENTER_TOKEN_REQUIRED` | enter_token 누락 |
| 401 | `SESSION_TOKEN_REQUIRED` | coreSessionToken 누락 |
| 403 | `ENTER_TOKEN_INVALID` | enter_token이 존재하지 않거나 만료 |
| 403 | `SESSION_TOKEN_INVALID` | coreSessionToken이 유효하지 않거나 만료 |
| 409 | `ALREADY_JOINED` | 이미 대기열에 등록됨 (멱등 처리로 기존 토큰 반환) |
| 409 | `SEAT_ALREADY_HELD` | 이미 다른 사용자가 홀드 중인 좌석 |
| 409 | `HOLD_EXPIRED` | 홀드가 만료됨 (1분 초과) |
| 409 | `ALREADY_HOLDING` | 이미 다른 좌석을 홀드 중 (1인 1좌석) |
| 409 | `SOLD_OUT` | 전 좌석 매진 |
| 429 | `TOO_MANY_REQUESTS` | Rate limit 초과 |
| 503 | `EVENT_NOT_OPEN` | 이벤트가 아직 오픈되지 않음 |

---

## 2.2 Queue Gate APIs

### 2.2.1 GET /gate/sync

클라이언트 카운트다운 동기화 + syncToken 발급.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| eventId | String | Y | 이벤트 ID |
| scheduleId | String | Y | 스케줄 ID |

**Response 200**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000",
  "serverTimeMs": 1738200000123,
  "startAtMs": 1738200005000,
  "syncToken": "base64url(hmac(...))",
}
```

**syncToken Claims (HMAC payload)**

```
eventId | scheduleId | startAtMs | issuedAtMs | nonce(UUID)
```

- Secret: `SYNC_TOKEN_SECRET` 환경변수
- 유효 기간: startAtMs + 15초까지

**Side Effect**

- `clientId` 쿠키를 UUID v4로 새로 발급
  - Cookie name: `cid`
  - HttpOnly: true
  - Secure: production only
  - SameSite: Lax
  - Max-Age: 7일

---

### 2.2.2 POST /gate/join

대기열 등록. receivedAt 기준으로 rank를 추정한다.

**Request Body**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000",
  "syncToken": "..."
}
```

- `clientId`는 쿠키에서 추출 (서버가 관리)

**Validation**

1. syncToken HMAC 검증
2. `receivedAtMs - startAtMs` = delta 계산
3. delta < 0 → `400 TOO_EARLY`
4. delta > join-window-after-ms → `400 INVALID_WINDOW`
5. `soldout:{eventId}:{scheduleId}` 존재 → `409 SOLD_OUT`
6. 중복 join → 기존 queueToken 반환 (멱등)

**Response 200**

```json
{
  "queueToken": "qt_a1b2c3d4",
  "estimatedRank": 12345,
  "sseUrl": "/gate/stream?queueToken=qt_a1b2c3d4"
}
```

**Rank Estimation Algorithm**

서버가 `deltaMs = receivedAtMs - startAtMs`를 계산하고 비선형 버킷에 매핑:

| Delta Range | Bucket Base | Bucket Range | Population |
|-------------|-------------|--------------|------------|
| 0 ~ 20ms | 0 | 1,000 | top 0.1% |
| 20 ~ 50ms | 1,000 | 4,000 | top 0.5% |
| 50 ~ 100ms | 5,000 | 5,000 | top 1% |
| 100 ~ 200ms | 10,000 | 20,000 | top 3% |
| 200 ~ 500ms | 30,000 | 70,000 | top 10% |
| 500ms ~ 2s | 100,000 | 200,000 | top 30% |
| 2s ~ 10s | 300,000 | 700,000 | ~100% |

- `estimatedRank = bucketBase`
- ZSET score = `estimatedRank * 10 + random(0, 9)` (tie-breaker)

---

### 2.2.3 GET /gate/stream?queueToken=...

SSE 스트리밍으로 대기열 상태를 실시간 전달.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| queueToken | String | Y | join 시 발급받은 토큰 |

**SSE Event Format**

```
event: queue.progress
data: {"queueToken":"qt_...","status":"WAITING","estimatedRank":12345,"approxPosition":9876,"estimatedWaitSec":142,"enterToken":null}

```

**Status Values**

| Status | Description |
|--------|-------------|
| `WAITING` | 대기 중 |
| `ADMISSION_GRANTED` | 입장권 발급됨 (`enterToken` 필드에 토큰 포함) |
| `EXPIRED` | 입장권 만료 또는 대기열 TTL 만료 |
| `SOLD_OUT` | 전 좌석 매진 |

**Push Policy**

- 기본 1초 주기
- 재연결 시 queueToken으로 상태 재조회 후 즉시 1건 push
- `SOLD_OUT` 감지 시 즉시 push 후 스트림 종료
- `ADMISSION_GRANTED` 시 enterToken 포함하여 push

---

### 2.2.4 GET /gate/status?queueToken=...

SSE fallback용 상태 조회 (polling).

**Response 200**

```json
{
  "queueToken": "qt_...",
  "status": "WAITING",
  "estimatedRank": 12345,
  "approxPosition": 9876,
  "estimatedWaitSec": 142,
  "enterToken": null,
  "serverTimeMs": 1738200001123
}
```

---

## 2.3 Ticketing Core APIs

### 2.3.1 POST /core/enter

enter_token으로 Core 입장 + coreSessionToken 발급 (핸드셰이크).

**Request Headers**

```
Authorization: Bearer <enter_token>
```

**Request Body**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000"
}
```

**Process (Lua 원자화)**

1. enter_token에서 jti 추출 + HMAC 검증
2. Redis `GET enter:{eventId}:{scheduleId}:{jti}` 확인
3. 유효하면 `DEL` (1회 소모)
4. `sessionId` 생성 (UUID)
5. `SET cs:{eventId}:{scheduleId}:{sessionId}` = clientId, EX 300
6. `SET csidx:{eventId}:{scheduleId}:{clientId}` = sessionId, EX 300
7. `SADD active:{eventId}:{scheduleId}` clientId

**Response 200**

```json
{
  "coreSessionToken": "cs_<uuid>.<sig>",
  "expiresInSec": 300,
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000"
}
```

**coreSessionToken Format**

```
cs_<uuid>.<hmac_signature>
sig = HMAC(CORE_SESSION_SECRET, uuid | eventId | scheduleId | expMs)
```

---

### 2.3.2 GET /core/seats?eventId=...&scheduleId=...

좌석 목록 조회 (zone별 그룹핑).

**Request Headers**

```
Authorization: Bearer <coreSessionToken>
```

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| eventId | String | Y | 이벤트 ID |
| scheduleId | String | Y | 스케줄 ID |

**Response 200**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000",
  "zones": [
    {
      "zone": "A",
      "seats": [
        { "seatId": "seat_001", "seatNo": 1, "status": "AVAILABLE" },
        { "seatId": "seat_002", "seatNo": 2, "status": "HELD" },
        { "seatId": "seat_003", "seatNo": 3, "status": "CONFIRMED" }
      ]
    },
    {
      "zone": "B",
      "seats": [ ... ]
    }
  ],
  "serverTimeMs": 1738200123456
}
```

**Seat Status**

| Status | Description |
|--------|-------------|
| `AVAILABLE` | 선택 가능 |
| `HELD` | 다른 사용자가 선점 중 (holder 정보 비노출) |
| `CONFIRMED` | 예매 확정됨 |

**Side Effect**

- coreSessionToken TTL 연장 (EXPIRE 300)

---

### 2.3.3 POST /core/holds

좌석 선점(홀드) 생성.

**Request Headers**

```
Authorization: Bearer <coreSessionToken>
```

**Request Body**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000",
  "seatId": "seat_042"
}
```

**Validation**

1. coreSessionToken 검증 (Redis lookup)
2. 해당 좌석이 AVAILABLE인지 확인
3. 해당 clientId가 이미 다른 좌석을 hold 중인지 확인

**Response 201**

```json
{
  "holdId": "hold_abc123",
  "scheduleId": "sc_2026_0101_2000",
  "seatId": "seat_042",
  "seatNo": 42,
  "zone": "A",
  "expiresAtMs": 1738200183456,
  "holdTtlSec": 60
}
```

**Error Cases**

- 이미 held → `409 SEAT_ALREADY_HELD`
- 이미 다른 좌석 hold 중 → `409 ALREADY_HOLDING`
- 전석 매진 → `409 SOLD_OUT`

**DB Constraint**

- `holds` 테이블: `UNIQUE(schedule_id, seat_id)` — 동일 좌석 중복 홀드 방지
- `holds` 테이블: `UNIQUE(schedule_id, client_id)` — 1인 1좌석 제한

---

### 2.3.4 POST /core/holds/{holdId}/confirm

홀드된 좌석을 확정(결제 모킹).

**Request Headers**

```
Authorization: Bearer <coreSessionToken>
```

**Validation**

1. coreSessionToken 검증
2. holdId가 해당 clientId 소유인지 확인
3. hold.expires_at >= now() 확인

**Response 200**

```json
{
  "reservationId": "res_xyz789",
  "scheduleId": "sc_2026_0101_2000",
  "seat": {
    "seatId": "seat_042",
    "zone": "A",
    "seatNo": 42
  },
  "confirmedAtMs": 1738200123456
}
```

**Process (DB Transaction)**

1. hold row 조회 + expires_at 검증
2. reservation row INSERT
3. hold row DELETE
4. Redis: `SREM active:{eventId}:{scheduleId}` clientId (세션 종료)
5. Redis: `DEL cs:{...}:{sessionId}`, `DEL csidx:{...}:{clientId}`

**Error Cases**

- hold 만료 → `409 HOLD_EXPIRED`
- 중복 confirm → 기존 reservationId 반환 (멱등)

**SOLD_OUT Detection**

- confirm 성공 후, 남은 AVAILABLE 좌석 수 확인
- 0이면 `SET soldout:{eventId}:{scheduleId} "1" EX 86400`
