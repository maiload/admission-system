# 3. Redis Key Design & Lua Scripts

## 3.1 Cluster Hash Tag Convention

모든 키는 `{eventId}`를 해시태그로 사용하여 동일 슬롯에 배치한다.
Lua 스크립트에서 다중 키 접근 시 같은 슬롯에 있어야 CROSSSLOT 에러를 방지할 수 있다.

> 문서 내에서는 가독성을 위해 `{evt}:{sch}`로 축약하되, 실제 해시태그는 `{evt}`만 적용된다.

---

## 3.2 Key Inventory

### Queue Gate Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `q:{evt}:{sch}:z` | ZSET | 없음 (이벤트 종료 시 정리) | 대기열. member=queueToken, score=estimatedRank*10+tieBreaker |
| `qstate:{evt}:{sch}:{queueToken}` | HASH | 30분 | 대기열 토큰 상태. fields: status, estimatedRank, clientId, enterToken, jti |
| `qjoin:{evt}:{sch}:{clientId}` | STRING | 30분 | 중복 join 방지. value=queueToken |

### Admission Worker Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `enter:{evt}:{sch}:{jti}` | STRING | 120초 | enter_token 저장. value=clientId |
| `rate:{evt}:{sch}:{epochSecond}` | STRING | 3초 | 초당 발급 카운터 |
| `active:{evt}:{sch}` | SET | 없음 (스케줄러 정리) | 코어 활성 세션. member=clientId |

### Ticketing Core Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `cs:{evt}:{sch}:{sessionId}` | STRING | 300초 (슬라이딩) | Core session. value=clientId |
| `csidx:{evt}:{sch}:{clientId}` | STRING | 300초 (슬라이딩) | 역인덱스. value=sessionId |

### Global Keys

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `soldout:{evt}:{sch}` | STRING | 86400초 (24h) | 매진 플래그. value="1" |
| `active_schedules` | ZSET | 없음 | 활성 스케줄 목록. member={eventId}:{scheduleId}, score=startAtMs |

---

## 3.3 Lua Script #1: Queue Join

**File**: `queue-join.lua`

**Purpose**: 중복 등록 방지 + 상태 저장 + 대기열 추가를 원자적으로 처리

### Keys

```
KEYS[1] = qjoin:{evt}:{sch}:{clientId}     -- 중복 체크 키
KEYS[2] = q:{evt}:{sch}:z                   -- 대기열 ZSET
KEYS[3] = qstate:{evt}:{sch}:{queueToken}   -- 상태 HASH
```

### Args

```
ARGV[1] = clientId
ARGV[2] = queueToken
ARGV[3] = score            -- estimatedRank * 10 + tieBreaker
ARGV[4] = estimatedRank
ARGV[5] = ttlSec           -- qstate/qjoin TTL (1800)
```

### Pseudocode

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

### Return

```
[0] = "EXISTING" | "CREATED"
[1] = queueToken
[2] = estimatedRank
```

---

## 3.4 Lua Script #2: Admission Issue

**File**: `admission-issue.lua`

**Purpose**: 멀티 워커 환경에서 상위 N명 선별 + enter_token 발급 + 상태 변경 + 제한 체크를 원자적으로 처리

### Keys

```
KEYS[1] = q:{evt}:{sch}:z                    -- 대기열 ZSET
KEYS[2] = rate:{evt}:{sch}:{epochSecond}      -- 초당 발급 카운터
KEYS[3] = active:{evt}:{sch}                  -- 활성 세션 SET
```

### Args

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

### Pseudocode

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

### Return

```
[0] = issuedCount
[1] = skippedCount
[2] = remainingQueueSize
```

### Note on Key Construction in Lua

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

---

## 3.5 Lua Script #3: Core Handshake

**File**: `core-handshake.lua`

**Purpose**: enter_token DEL + core session SET + active SADD를 원자적으로 처리

### Keys

```
KEYS[1] = enter:{evt}:{sch}:{jti}             -- enter_token
KEYS[2] = cs:{evt}:{sch}:{sessionId}           -- core session
KEYS[3] = csidx:{evt}:{sch}:{clientId}         -- 역인덱스
KEYS[4] = active:{evt}:{sch}                   -- 활성 세션 SET
```

### Args

```
ARGV[1] = clientId
ARGV[2] = sessionId
ARGV[3] = sessionTtlSec     -- 300
```

### Pseudocode

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

### Return

```
[0] = "OK" | "INVALID" | "MISMATCH"
[1] = sessionId (OK인 경우)
```

---

## 3.6 Key Lifecycle Summary

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
