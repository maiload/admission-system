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
-- ARGV[3] = ttlSec
-- ARGV[4] = loadTest ("1"|"0")
--
-- Returns: { "EXISTING"|"CREATED", queueToken }

local joinKey   = KEYS[1]
local queueKey  = KEYS[2]
local stateKey  = KEYS[3]
local seqKey    = KEYS[4]

local clientId      = ARGV[1]
local queueToken    = ARGV[2]
local ttlSec        = tonumber(ARGV[3])
local loadTest      = ARGV[4]

-- 1. Check for existing join (idempotent)
local existing = redis.call('GET', joinKey)
if existing then
    local existToken = existing
    return { "EXISTING", existToken }
end

-- 2. Compute score with sequence tie-breaker
local seq = redis.call('INCR', seqKey)
if ttlSec and ttlSec > 0 then
    local ttl = redis.call('TTL', seqKey)
    if ttl < 0 then
        redis.call('EXPIRE', seqKey, ttlSec)
    end
end
local score = seq

-- 3. ZADD to queue
redis.call('ZADD', queueKey, score, queueToken)


-- 4. HSET state
local time = redis.call('TIME')
local joinedAtMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
redis.call('HSET', stateKey,
    'status', 'WAITING',
    'joinedAtMs', joinedAtMs,
    'clientId', clientId,
    'enterToken', '',
    'jti', '',
    'loadTest', loadTest
)
redis.call('EXPIRE', stateKey, ttlSec)

-- 5. Store join marker: clientId → queueToken
redis.call('SET', joinKey, queueToken, 'EX', ttlSec)

return { "CREATED", queueToken }
