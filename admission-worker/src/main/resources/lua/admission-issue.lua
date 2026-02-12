-- admission-issue.lua
-- Atomically pop queue members and issue enter tokens.
--
-- KEYS[1] = q:{evt}:{sch}:z              (queue ZSET)
-- KEYS[2] = rate:{evt}:{sch}:{epochSec}  (rate counter STRING)
-- KEYS[3] = active:{evt}:{sch}           (active sessions SET)
--
-- ARGV[1]  = maxIssue
-- ARGV[2]  = rateCap
-- ARGV[3]  = concurrencyCap
-- ARGV[4]  = enterTtlSec
-- ARGV[5]  = qstateTtlSec
-- ARGV[6]  = rateTtlSec
-- ARGV[7]  = hashTag         ({evt}:{sch})
-- ARGV[8]  = tokenCount      (number of jti/token pairs)
-- ARGV[9..] = jti1, token1, jti2, token2, ...
--
-- Returns: { issuedCount, skippedCount, remainingQueueSize }

local queueKey  = KEYS[1]
local rateKey   = KEYS[2]
local activeKey = KEYS[3]

local maxIssue       = tonumber(ARGV[1])
local rateCap        = tonumber(ARGV[2])
local concurrencyCap = tonumber(ARGV[3])
local enterTtlSec    = tonumber(ARGV[4])
local qstateTtlSec   = tonumber(ARGV[5])
local rateTtlSec     = tonumber(ARGV[6])
local hashTag        = ARGV[7]
local tokenCount     = tonumber(ARGV[8])

-- 1. Calculate how many we can issue
local currentRate   = tonumber(redis.call('GET', rateKey) or '0')
local currentActive = redis.call('SCARD', activeKey)

local rateRoom       = rateCap - currentRate
local concurrencyRoom = concurrencyCap - currentActive
local issueCount = math.min(maxIssue, math.max(0, rateRoom), math.max(0, concurrencyRoom))
issueCount = math.min(issueCount, tokenCount)

if issueCount <= 0 then
    local remaining = redis.call('ZCARD', queueKey)
    return { 0, 0, remaining }
end

-- 2. ZPOPMIN from queue
local popped = redis.call('ZPOPMIN', queueKey, issueCount)
-- popped = { member1, score1, member2, score2, ... }

local issued  = 0
local skipped = 0
local tokenIdx = 9  -- first jti index

for i = 1, #popped, 2 do
    local queueToken = popped[i]

    local jti        = ARGV[tokenIdx]
    local enterToken = ARGV[tokenIdx + 1]
    tokenIdx = tokenIdx + 2

    local stateKey = 'qstate:' .. hashTag .. ':' .. queueToken
    local status = redis.call('HGET', stateKey, 'status')

    if status == 'WAITING' then
        local clientId = redis.call('HGET', stateKey, 'clientId')
        local loadTest = redis.call('HGET', stateKey, 'loadTest')
        redis.call('HSET', stateKey,
            'status', 'ADMISSION_GRANTED',
            'enterToken', enterToken,
            'jti', jti
        )
        redis.call('EXPIRE', stateKey, qstateTtlSec)

        -- allow re-join after admission by clearing qjoin
        local joinKey = 'qjoin:' .. hashTag .. ':' .. clientId
        redis.call('DEL', joinKey)

        local enterKey = 'enter:' .. hashTag .. ':' .. jti
        redis.call('SET', enterKey, clientId .. '|' .. (loadTest or '0'), 'EX', enterTtlSec)

        issued = issued + 1
    else
        skipped = skipped + 1
    end
end

-- 3. Update rate counter
if issued > 0 then
    redis.call('INCRBY', rateKey, issued)
    redis.call('EXPIRE', rateKey, rateTtlSec)
end

local remaining = redis.call('ZCARD', queueKey)
return { issued, skipped, remaining }
