-- Core Handshake Lua Script
-- Atomically: validate enter_token → DEL → create session → SADD active
--
-- KEYS[1] = enter:{evt}:{sch}:{jti}
-- KEYS[2] = cs:{evt}:{sch}:{sessionId}
-- KEYS[3] = active:{evt}:{sch}
--
-- ARGV[1] = clientId
-- ARGV[2] = sessionId
-- ARGV[3] = sessionTtlSec
-- ARGV[4] = hashTag prefix (for old session cleanup / csidx key)

local storedClientId = redis.call('GET', KEYS[1])
if not storedClientId then
    return 'INVALID'
end

-- clientId check: if ARGV[1] is empty, use stored value
local clientId = ARGV[1]
if clientId == '' then
    clientId = storedClientId
end

-- Delete enter token (1-time use)
redis.call('DEL', KEYS[1])

local csidxKey = 'csidx:' .. ARGV[4] .. ':' .. clientId

-- Clean up old session if exists
local oldSessionId = redis.call('GET', csidxKey)
if oldSessionId then
    redis.call('DEL', 'cs:' .. ARGV[4] .. ':' .. oldSessionId)
end

-- Create new session
redis.call('SET', KEYS[2], clientId, 'EX', tonumber(ARGV[3]))
redis.call('SET', csidxKey, ARGV[2], 'EX', tonumber(ARGV[3]))

-- Add to active set
redis.call('SADD', KEYS[3], clientId)

return clientId
