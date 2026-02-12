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
-- ARGV[4] = loadTestTtlSec

local stored = redis.call('GET', KEYS[1])
if not stored then
    return 'INVALID'
end

local parts = {}
for part in stored:gmatch("[^|]+") do
    parts[#parts + 1] = part
end
local clientId = parts[1]
local loadTest = parts[2] or "0"

-- Delete enter token
redis.call('DEL', KEYS[1])

local csidxKey = 'csidx:' .. ARGV[3] .. ':' .. clientId

-- Clean up old session if exists
local oldSessionId = redis.call('GET', csidxKey)
if oldSessionId then
    redis.call('DEL', 'cs:' .. ARGV[3] .. ':' .. oldSessionId)
end

-- Create new session
local ttl = tonumber(ARGV[2])
local loadTtl = tonumber(ARGV[4]) or ttl
if loadTest == "1" then
    ttl = loadTtl
end
redis.call('SET', KEYS[2], clientId, 'EX', ttl)
redis.call('SET', csidxKey, ARGV[1], 'EX', ttl)

-- Add to active set
redis.call('SADD', KEYS[3], clientId)

return clientId
