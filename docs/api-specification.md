# API Specification

## 1. Error Response Format (공통)

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
| 400 | `INVALID_REQUEST` | 일반적인 요청 파라미터 오류 |
| 401 | `ENTER_TOKEN_REQUIRED` | enter_token 누락 |
| 401 | `SESSION_TOKEN_REQUIRED` | coreSessionToken 누락 |
| 403 | `ENTER_TOKEN_INVALID` | enter_token이 존재하지 않거나 만료 |
| 403 | `SESSION_TOKEN_INVALID` | coreSessionToken이 유효하지 않거나 만료 |
| 409 | `ALREADY_JOINED` | 이미 대기열에 등록됨 (멱등 처리로 기존 토큰 반환) |
| 409 | `SEAT_ALREADY_HELD` | 이미 다른 사용자가 홀드 중인 좌석 |
| 409 | `HOLD_EXPIRED` | 홀드가 만료됨 |
| 409 | `ALREADY_HOLDING` | 이미 다른 좌석을 홀드 중 |
| 409 | `SOLD_OUT` | 전 좌석 매진 |
| 429 | `TOO_MANY_REQUESTS` | Rate limit 초과 |

---

## 2. Queue Gate APIs

### 2.1 POST /gate/join

대기열 등록. clientId 쿠키 기반으로 사용자를 식별한다.

**Request Body**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000"
}
```

- `clientId`는 쿠키(`cid`)에서 추출. 없으면 서버가 UUID로 신규 발급하여 쿠키에 세팅.

**Response 200**

```json
{
  "queueToken": "qt_a1b2c3d4",
  "sseUrl": "/gate/stream?queueToken=qt_a1b2c3d4&eventId=ev_2026_0101&scheduleId=sc_2026_0101_2000",
  "alreadyJoined": false
}
```

| Field | Description |
|-------|-------------|
| `queueToken` | 대기열 토큰 (SSE 구독, 상태 조회에 사용) |
| `sseUrl` | SSE 스트림 URL |
| `alreadyJoined` | 이미 대기열에 등록된 경우 true (멱등) |

**Side Effect**

- `clientId` 쿠키가 없는 경우 새로 발급
  - Cookie name: `cid`
  - HttpOnly: true
  - Path: `/`
  - Max-Age: 7일

**부하 테스트 모드**

- `X-Load-Test: true` 헤더를 전달하면 부하 테스트 모드로 동작

---

### 2.2 GET /gate/stream

SSE 스트리밍으로 대기열 상태를 실시간 전달.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| queueToken | String | Y | join 시 발급받은 토큰 |
| eventId | String | Y | 이벤트 ID |
| scheduleId | String | Y | 스케줄 ID |

**SSE Event Format**

```
event: queue.progress
data: {"status":"WAITING","rank":12345,"totalInQueue":50000,"enterToken":null,"eventId":"ev_2026_0101","scheduleId":"sc_2026_0101_2000"}

```

**Status Values**

| Status | Description |
|--------|-------------|
| `WAITING` | 대기 중 |
| `ADMISSION_GRANTED` | 입장권 발급됨 (`enterToken` 필드에 토큰 포함) |
| `EXPIRED` | 입장권 만료 또는 대기열 TTL 만료 |
| `SOLD_OUT` | 전 좌석 매진 |

**Response Fields**

| Field | Type | Description |
|-------|------|-------------|
| `status` | String | 대기열 상태 |
| `rank` | long | 현재 순위 |
| `totalInQueue` | long | 전체 대기열 크기 |
| `enterToken` | String? | ADMISSION_GRANTED 시 입장 토큰 |
| `eventId` | String | 이벤트 ID |
| `scheduleId` | String | 스케줄 ID |

---

### 2.3 GET /gate/status

SSE fallback용 상태 조회 (polling).

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| queueToken | String | Y | join 시 발급받은 토큰 |
| eventId | String | Y | 이벤트 ID |
| scheduleId | String | Y | 스케줄 ID |

**Response 200**

```json
{
  "status": "WAITING",
  "rank": 12345,
  "totalInQueue": 50000,
  "enterToken": null,
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000"
}
```

---

## 3. Ticketing Core APIs

인증은 쿠키 기반 세션을 사용한다. `POST /core/enter` 성공 시 `coreSessionToken`이 쿠키로 설정되며, 이후 요청에서 자동 전송된다.

### 3.1 POST /core/enter

enterToken으로 Core 입장 + coreSessionToken 쿠키 발급 (핸드셰이크).

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

1. enterToken에서 jti 추출 + HMAC 검증
2. Redis `GET enter:{eventId}:{scheduleId}:{jti}` 확인
3. 유효하면 `DEL` (1회 소모)
4. `sessionId` 생성 (UUID)
5. `SET cs:{eventId}:{scheduleId}:{sessionId}` = clientId, EX 300
6. `SET csidx:{eventId}:{scheduleId}:{clientId}` = sessionId, EX 300
7. `SADD active:{eventId}:{scheduleId}` clientId

**Response 200**

```json
{
  "sessionTtlSec": 300,
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000"
}
```

- `coreSessionToken`은 응답 body가 아닌 **Set-Cookie 헤더**로 전달됨

**멱등성**

- 이미 유효한 세션 쿠키가 있고 동일 event/schedule이면 기존 세션을 재사용 (enterToken 소모 없음)

---

### 3.2 GET /core/seats

좌석 목록 조회 (zone별 그룹핑).

**인증**: 쿠키 기반 coreSessionToken

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
    }
  ],
  "serverTimeMs": 1738200123456
}
```

**Seat Status**

| Status | Description |
|--------|-------------|
| `AVAILABLE` | 선택 가능 |
| `HELD` | 다른 사용자가 선점 중 |
| `CONFIRMED` | 예매 확정됨 |

**Side Effect**

- coreSessionToken TTL 연장 (슬라이딩 세션)

---

### 3.3 POST /core/holds

복수 좌석 선점(홀드) 생성. holdGroup 단위로 관리된다.

**인증**: 쿠키 기반 coreSessionToken

**Request Body**

```json
{
  "eventId": "ev_2026_0101",
  "scheduleId": "sc_2026_0101_2000",
  "seatIds": ["seat_042", "seat_043"]
}
```

**Response 201**

```json
{
  "holdGroupId": "hold_group_abc123",
  "scheduleId": "sc_2026_0101_2000",
  "seats": [
    { "seatId": "seat_042", "seatNo": 42, "zone": "A" },
    { "seatId": "seat_043", "seatNo": 43, "zone": "A" }
  ],
  "expiresAtMs": 1738200183456,
  "holdTtlSec": 60
}
```

**Validation**

1. coreSessionToken 쿠키 검증 (Redis lookup)
2. 세션 TTL 연장 (슬라이딩)
3. 해당 좌석들이 AVAILABLE인지 확인
4. 해당 clientId가 이미 다른 좌석을 hold 중인지 확인

**Error Cases**

- 이미 held → `409 SEAT_ALREADY_HELD`
- 이미 다른 좌석 hold 중 → `409 ALREADY_HOLDING`
- 전석 매진 → `409 SOLD_OUT`

**DB Constraint**

- `holds` 테이블: `UNIQUE(schedule_id, seat_id)` — 동일 좌석 중복 홀드 방지
- `holds` 테이블: `UNIQUE(schedule_id, client_id)` — 1인 1그룹 제한

---

### 3.4 POST /core/holds/{holdGroupId}/confirm

홀드된 좌석 그룹을 확정(결제 모킹).

**인증**: 쿠키 기반 coreSessionToken

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| holdGroupId | UUID | 홀드 그룹 ID |

**Validation**

1. coreSessionToken 쿠키 검증
2. holdGroupId가 해당 clientId 소유인지 확인
3. hold가 만료되지 않았는지 확인

**Response 200**

```json
{
  "scheduleId": "sc_2026_0101_2000",
  "seats": [
    {
      "reservationId": "res_xyz789",
      "seatId": "seat_042",
      "zone": "A",
      "seatNo": 42
    }
  ],
  "confirmedAtMs": 1738200123456
}
```

**Process (DB Transaction)**

1. hold rows 조회 + expires_at 검증
2. reservation rows INSERT
3. hold rows DELETE
4. Redis: `SREM active:{eventId}:{scheduleId}` clientId (세션 종료)
5. Redis: `DEL cs:{...}:{sessionId}`, `DEL csidx:{...}:{clientId}`

**Error Cases**

- hold 만료 → `409 HOLD_EXPIRED`

**SOLD_OUT Detection**

- confirm 성공 후, 남은 AVAILABLE 좌석 수 확인
- 0이면 `SET soldout:{eventId}:{scheduleId} "1" EX 86400`

---

## 4. Admin / Schedule APIs

### 4.1 GET /core/schedules/active

활성 스케줄 목록 조회.

**Response 200**

```json
[
  {
    "eventId": "ev_2026_0101",
    "scheduleId": "sc_2026_0101_2000",
    ...
  }
]
```

### 4.2 GET /core/admin/schedules/activate

DB의 전체 스케줄을 Redis `active_schedules`에 등록.

**Response 200**

```json
{
  "activated": 5
}
```

### 4.3 DELETE /core/admin/schedules

Redis `active_schedules`를 초기화.

**Response 200**

```json
{
  "deleted": 5
}
```
