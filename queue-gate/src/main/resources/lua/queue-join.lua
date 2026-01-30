-- queue-join.lua
-- Atomically join the queue with duplicate prevention.
--
-- KEYS[1] = qjoin:{evt}:{sch}:{clientId}   (duplicate check STRING)
-- KEYS[2] = q:{evt}:{sch}:z                (queue ZSET)
-- KEYS[3] = qstate:{evt}:{sch}:{queueToken} (state HASH)
--
-- ARGV[1] = clientId
-- ARGV[2] = queueToken
-- ARGV[3] = score
-- ARGV[4] = estimatedRank
-- ARGV[5] = ttlSec
--
-- Returns: { "EXISTING"|"CREATED", queueToken, estimatedRank }

local joinKey   = KEYS[1]
local queueKey  = KEYS[2]
local stateKey  = KEYS[3]

local clientId      = ARGV[1]
local queueToken    = ARGV[2]
local score         = tonumber(ARGV[3])
local estimatedRank = ARGV[4]
local ttlSec        = tonumber(ARGV[5])

-- 1. Check for existing join (idempotent)
local existing = redis.call('GET', joinKey)
if existing then
    -- Client already joined — retrieve existing state
    local existingToken = existing
    -- Build existing state key from the stored queueToken
    -- The join key stores "queueToken|estimatedRank"
    local parts = {}
    for part in existing:gmatch("[^|]+") do
        parts[#parts + 1] = part
    end
    local existToken = parts[1]
    local existRank  = parts[2] or "0"
    return { "EXISTING", existToken, existRank }
end

-- 2. ZADD to queue
redis.call('ZADD', queueKey, score, queueToken)

-- 3. HSET state
redis.call('HSET', stateKey,
    'status', 'WAITING',
    'estimatedRank', estimatedRank,
    'clientId', clientId,
    'enterToken', '',
    'jti', ''
)
redis.call('EXPIRE', stateKey, ttlSec)

-- 4. Store join marker: clientId → "queueToken|estimatedRank"
redis.call('SET', joinKey, queueToken .. '|' .. estimatedRank, 'EX', ttlSec)

return { "CREATED", queueToken, estimatedRank }
