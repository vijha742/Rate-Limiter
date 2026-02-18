-- KEYS[1] : key {ip, api-key, user-token}
-- ARGV[1] : capacity
-- ARGV[2] : ttl

local current = redis.call("INCR", KEYS[1])

if tonumber(current) == 1 then
	redis.call("expire", KEYS[1], ARGV[2])
end

local allowed = 0
if tonumber(current) <= tonumber(ARGV[1]) then
	allowed = 1
end

local ttl = redis.call("ttl", KEYS[1])

return { allowed, current, ttl }
