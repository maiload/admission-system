-- Core Handshake Lua Script
-- Atomically: validate enter_token → DEL → create session → SADD active
--
-- KEYS[1] = enter:{evt}:{sch}:{jti}
-- KEYS[2] = cs:{evt}:{sch}:{sessionId}
-- KEYS[3] = active:{evt}:{sch}
--
-- ARGV[1] = sessionId
-- ARGV[2] = sessionTtlSec
-- ARGV[3] = hashTag prefix (for old session cleanup / csidx key)

local storedClientId = redis.call('GET', KEYS[1])
if not storedClientId then
    return 'INVALID'
end

local clientId = storedClientId

-- Delete enter token
redis.call('DEL', KEYS[1])

local csidxKey = 'csidx:' .. ARGV[3] .. ':' .. clientId

-- Clean up old session if exists
local oldSessionId = redis.call('GET', csidxKey)
if oldSessionId then
    redis.call('DEL', 'cs:' .. ARGV[3] .. ':' .. oldSessionId)
end

-- Create new session
redis.call('SET', KEYS[2], clientId, 'EX', tonumber(ARGV[2]))
redis.call('SET', csidxKey, ARGV[1], 'EX', tonumber(ARGV[2]))

-- Add to active set
redis.call('SADD', KEYS[3], clientId)

return clientId
