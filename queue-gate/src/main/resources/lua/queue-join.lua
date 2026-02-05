-- queue-join.lua
-- Atomically join the queue with duplicate prevention.
--
-- KEYS[1] = qjoin:{evt}:{sch}:{clientId}   (duplicate check STRING)
-- KEYS[2] = q:{evt}:{sch}:z                (queue ZSET)
-- KEYS[3] = qstate:{evt}:{sch}:{queueToken} (state HASH)
-- KEYS[4] = qseq:{evt}:{sch}                (score SEQ)
--
-- ARGV[1] = clientId
-- ARGV[2] = queueToken
-- ARGV[3] = estimatedRank
-- ARGV[4] = ttlSec
--
-- Returns: { "EXISTING"|"CREATED", queueToken, estimatedRank }

local joinKey   = KEYS[1]
local queueKey  = KEYS[2]
local stateKey  = KEYS[3]
local seqKey    = KEYS[4]

local clientId      = ARGV[1]
local queueToken    = ARGV[2]
local estimatedRank = ARGV[3]
local ttlSec        = tonumber(ARGV[4])

-- 1. Check for existing join (idempotent)
local existing = redis.call('GET', joinKey)
if existing then
    local existingToken = existing
    local parts = {}
    for part in existing:gmatch("[^|]+") do
        parts[#parts + 1] = part
    end
    local existToken = parts[1]
    local existRank  = parts[2] or "0"
    return { "EXISTING", existToken, existRank }
end

-- 2. Compute score with sequence tie-breaker
local seq = redis.call('INCR', seqKey)
if ttlSec and ttlSec > 0 then
    local ttl = redis.call('TTL', seqKey)
    if ttl < 0 then
        redis.call('EXPIRE', seqKey, ttlSec)
    end
end
local score = tonumber(estimatedRank) + (seq % 1000000) * 0.000001

-- 3. ZADD to queue
redis.call('ZADD', queueKey, score, queueToken)

-- 4. HSET state
local time = redis.call('TIME')
local joinedAtMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
redis.call('HSET', stateKey,
    'status', 'WAITING',
    'estimatedRank', estimatedRank,
    'joinedAtMs', joinedAtMs,
    'clientId', clientId,
    'enterToken', '',
    'jti', ''
)
redis.call('EXPIRE', stateKey, ttlSec)

-- 5. Store join marker: clientId → "queueToken|estimatedRank"
redis.call('SET', joinKey, queueToken .. '|' .. estimatedRank, 'EX', ttlSec)

return { "CREATED", queueToken, estimatedRank }
