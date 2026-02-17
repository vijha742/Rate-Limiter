-- KEYS[1] = sorted set key

-- ARGV[1] = window_size (ms)
-- ARGV[2] = max_requests
-- ARGV[3] = current_time (ms)
-- ARGV[4] = ttl (ms)

local window = tonumber(ARGV[1])
local max_requests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- remove expired entries
redis.call("ZREMRANGEBYSCORE", KEYS[1], 0, now - window)

-- count current requests in window
local current = redis.call("ZCARD", KEYS[1])

local allowed = 0

if current < max_requests then
	allowed = 1
	redis.call("ZADD", KEYS[1], now, now)
end

redis.call("PEXPIRE", KEYS[1], ttl)

return { allowed, current }
-- return allowed
